(ns dk.cst.glossematics.frontend.page.reader
  "Page containing a synchronized facsimile & TEI transcription reader."
  (:require [clojure.string :as str]
            [shadow.resource :as resource]
            [kitchen-async.promise :as p]
            [reitit.frontend.easy :as rfe]
            [dk.cst.cuphic :as cup]
            [dk.cst.cuphic.xml :as xml]
            [rescope.core :as rescope]
            [rescope.helpers :as helpers]
            [rescope.style :as style]
            [dk.cst.hiccup-tools :as ht]
            [dk.cst.stucco.pattern :as pattern]
            [dk.cst.stucco.group :as group]
            [dk.cst.stucco.document :as document]
            [dk.cst.stucco.util.css :as css]
            [dk.cst.glossematics.db.tei :as db.tei]
            [dk.cst.glossematics.frontend.page.search :as search-page]
            [dk.cst.glossematics.frontend.state :as state :refer [db]]
            [dk.cst.glossematics.frontend.api :as api]
            [dk.cst.glossematics.frontend.shared :as fshared]
            [dk.cst.glossematics.static-data :as sd]
            [dk.cst.glossematics.frontend.i18n :as i18n]))

(def tei-css
  "Styles used for TEI documents specifically. They are written in a regular CSS
  file and then processed to work on the generated HTML."
  (style/prefix-css "tei" (resource/inline "dk/cst/glossematics/public/css/tei.css")))

(def theme+tei-css
  "The complete set of styles (widgets and TEI documents)."
  (str css/shadow-style "\n\n/*\n\t === tei.css ===\n*/\n" tei-css))

;; TODO: convert to i18n ns translations?
(defn da-type
  [type]
  (let [da?     (= "da" @state/language)
        type->s (if da?
                  {"conference" "denne konference"
                   "term"       "dette begreb"
                   "language"   "dette sprog"
                   "org"        "denne organisation"
                   "pers"       "denne person"
                   "place"      "dette sted"
                   "publ"       "denne udgivelse"
                   "receiver"   "denne modtager"
                   "sender"     "denne afsender"}
                  {"conference" "this conference"
                   "term"       "this term"
                   "language"   "this language"
                   "org"        "this organisation"
                   "pers"       "this person"
                   "place"      "this place"
                   "publ"       "this publication"
                   "receiver"   "this recipient"
                   "sender"     "this sender"})]
    (if da?
      (str "Vis mere om " (type->s type "dette"))
      (str "Show more about " (type->s type "this")))))


(def simplify-c
  (cup/->transformer
    '[:c {} ???]

    (fn [m]
      (let [[tag attr content] (:source (meta m))]
        (or content " ")))))

(def simplify-w
  (cup/->transformer
    '[:w {} ???]

    (fn [m]
      (let [[tag attr & content] (:source (meta m))]
        (if (every? string? content)
          (apply str content)
          (into [:span] content))))))

(defn merge-strings*
  [coll]
  (loop [ret  []
         [x & coll] coll
         strs []]
    (cond
      (string? x)
      (recur ret coll (conj strs x))

      (some? x)
      (if (not-empty strs)
        (recur (into ret [(apply str strs) x]) coll [])
        (recur (conj ret x) coll strs))

      (nil? x)
      (if (not-empty strs)
        (conj ret (apply str strs))
        ret))))

(def merge-strings
  (cup/->transformer
    '[_ {} ???]

    (fn [m]
      (let [[tag attr & content] (:source (meta m))]
        (into [tag attr] (merge-strings* content))))))

(def lb-as-br
  (cup/->transformer '[:lb] '[:br]))

(def list-as-ul
  (cup/->transformer
    '[:list (... list-items)]

    (fn [{:syms [list-items]}]
      (into [:ul] (for [[tag attr & content] list-items]
                    (into [:li] content))))))

(def ref-as-anchor
  (cup/->transformer
    '[_ {:ref  ref
         :type ?type} ???]

    (fn [{:syms [ref ?type]}]
      [:a {:href  (fshared/search-href ref)
           :title (da-type ?type)}
       [:slot]])))

(def language-as-anchor
  (cup/->transformer
    '[_ {:type "language"
         :n    ref} ???]

    (fn [{:syms [ref]}]
      [:a {:href  (fshared/search-href ref)
           :title (da-type "language")}
       [:slot]])))

(def date-as-time
  (cup/->transformer
    '[:date {:when when} ???]

    (fn [{:syms [when]}]
      [:time {:date-time when}
       [:slot]])))

(defn- pb?
  [x]
  (and (vector? x)
       (= :pb (first x))))

(declare inner-stage)

;; Unlike the 'outer-stage', the 'inner-stage' transformations can be safely
;; memoised since they don't have any side-effects.
(def rewrite-page
  (memoize #(cup/rewrite % inner-stage)))

(defn update-tei-carousel!
  "Updates the `carousel-state` when new `kvs` are detected."
  [carousel-state kvs]
  (let [old-kvs (:kvs @carousel-state)]
    (when (not= (map first kvs) (map first old-kvs))
      (reset! carousel-state {:i 0 :kvs kvs}))))

;; TODO: needed?
(defn- entity-aware
  "Apply `entity` metadata knowledge to the Hiccup `container` element."
  [{:keys [document/condition] :as entity} container]
  (let [handwritten? (and (get condition "handwritten")
                          (not (get condition "typed")))]
    (cond-> container

      ;; Fully handwritten documents are *NOT* marked as such in the text body,
      ;; but rather in the document header, so that knowledge needs to be
      ;; marked directly in the container element.
      handwritten? (assoc-in [1 :rend] "handwritten"))))

(defn paginate
  "Paginate the following `nodes` every time (pred node) is true for `pred`."
  [pred nodes]
  (->> (drop-while (complement pred) nodes)                 ; pre-page content
       (reduce
         (fn [pages node]
           (if (pred node)
             (conj pages [:page node])
             (update pages (dec (count pages)) conj node)))
         [])))

(defn body->pages
  "Divide the Hiccup `body` into pages for every [:pb]."
  [body]
  (->> (ht/split-hiccup pb? body :retain :between)
       (ht/node-children)
       (drop-while (complement pb?))
       (paginate pb?)))

;; Fairly complex transformer that restructures sibling-level page content into
;; a stucco carousel component. The large amount of content captured as page
;; content has to be explicitly rewritten in a separate call; otherwise, it will
;; be skipped entirely.
(defn pages-in-carousel
  "Get an `entity`-aware transformer for arranging a TEI document into pages."
  [entity]
  (cup/->transformer
    '[:body (... content)]

    (fn [m]
      (let [pages (body->pages (:source (meta m)))
            kvs   (for [[_ [_ {:keys [n facs]}] :as page] pages]
                    [(str "Side " n "; facs. " facs ".")
                     (rewrite-page page)])]
        ;; Currently, TEI data is updated on the page by way of a side-effect.
        ;; I'm unsure if there is a better way to do this.
        (update-tei-carousel! state/tei-carousel kvs)
        [pattern/carousel state/tei-carousel
         {:aria-label "Facsimile"}]))))

(def default-fn
  "This function is applied as a final step in every transformation. Each XML
  tag is prefixed with 'tei-' making it a valid HTML tag name, while xml:lang
  and xml:id are both converted into the non-namespaced HTML varieties."
  (helpers/default-fn {:prefix    "tei"
                       :attr-kmap {:xml/lang :lang
                                   :xml/id   :id}}))

;; For now, it seems impossible to not wrap every modified component, even if it
;; is a bit ineffecient. The issues are
;;   1) Cuphic deals poorly with Hiccup turning into reagent components.
;;   2) the current Cuphic patterns rely on shadow-dom features, e.g. slot.
(defn shadow-dom-wrapper
  "Each node is wrapped in a shadow DOM, allowing for an inlined style element
  and general isolation from the outer document style.

  This mostly preserves the XML structure in the generated HTML, while also
  allowing for discreet structural changes using <slot>."
  [old-node new-node]
  (let [node-with-css (constantly [:<> [:style theme+tei-css] new-node])]
    (vary-meta old-node assoc :ref (rescope/shadow-ref node-with-css))))

(def pre-stage
  "Makes actual structural changes to the TEI content."
  {:transformers [simplify-c
                  simplify-w]})

(def merge-stage
  {:transformers [merge-strings]})

(def inner-stage
  "Makes virtual changes to TEI document features using the shadow DOM."
  {:transformers [#_ref-as-anchor
                  #_language-as-anchor
                  lb-as-br
                  list-as-ul
                  date-as-time]
   :wrapper      shadow-dom-wrapper
   :default      default-fn})

;; Note that this step *cannot* be memoised since the document rendering relies
;; on side-effects executed inside the 'pages-in-carousel' transformation.
(defn ->outer-stage
  "Places all TEI pages inside a carousel component in a shadow DOM."
  [entity]
  {:transformers [(pages-in-carousel entity)]
   :wrapper      shadow-dom-wrapper
   :default      default-fn})

(def parse
  (memoize xml/parse))

(defn facs-id->facs-page
  [tr id]
  (let [url (fshared/backend-url (str "/file/" id ".jpg"))]
    [id [document/illustration {:src url
                                :alt (tr ::illustration-of-1 id)}]]))

(defn normalize-facs
  "Turn `raw-facsimile` into a sorted sequential collection."
  [raw-facsimile]
  (cond
    (coll? raw-facsimile)
    (sort raw-facsimile)

    (some? raw-facsimile)
    [raw-facsimile]))

;; Keep track of document fetches
(def ^:dynamic *current-fetch* nil)

(defn set-content!
  "Change the `document` currently displayed in the reader.

  Optionally, an `xml` string may be provided to parse as a TEI document.
  This feature is used to preview local TEI documents in Glossematics."
  [document & [xml]]
  (set! *current-fetch* document)
  ;; Should not display old state while waiting for the network request.
  (when (not= document (:document @state/reader))
    (swap! state/reader assoc :i 0 :document nil))

  ;; TODO: fix :tei-kvs side-effect, makes it hard to implement history/cache
  (p/let [tei              (or xml (api/fetch (str "/file/" document)))
          entity           (if xml
                             (-> (db.tei/->triples document xml)
                                 (db.tei/triples->entity))
                             (api/fetch (str "/entity/" document)))
          raw-hiccup       (parse tei)
          tr               (i18n/->tr)
          facs             (->> (normalize-facs (:document/facsimile entity))
                                (mapv (partial facs-id->facs-page tr)))
          outer-stage      (->outer-stage entity)
          rewritten-hiccup (cup/rewrite raw-hiccup pre-stage merge-stage outer-stage)
          tei-kvs          (:kvs @state/tei-carousel)
          missing-count    (- (count facs) (count tei-kvs))
          placeholder      (tr ::placeholder)]
    (swap! state/reader assoc

           :document document
           :entity entity
           :tei tei
           :hiccup rewritten-hiccup
           :facs-kvs facs

           ;; Perhaps a bit confusingly, the value of the :tei-kvs key is set as
           ;; a side-effect of the cup/rewrite call above. Below, the count of
           ;; :tei-kvs is compared to the count of the :facs-kvs and placeholder
           ;; content is inserted for any missing pages. This is done to ensure
           ;; that the two carousel widgets are able to stay synchronized in
           ;; situations where the system *doesn't* produce valid TEI content.
           :tei-kvs (if (not= 0 missing-count)
                      (concat tei-kvs (repeat missing-count placeholder))
                      tei-kvs))))

(defn entity-meta
  [tr search-state entity]
  (let [entity' (dissoc entity :file/extension)             ; not interesting
        kvs     (concat (remove nil? (for [k sd/reader-rels]
                                       (when-let [v (get entity' k)]
                                         [k v])))
                        (sort-by first (apply dissoc entity' sd/reader-rels)))]
    [fshared/metadata-table tr search-state entity kvs]))

(defn pdf-object
  [pdf-src]
  [:object {:data  pdf-src
            :type  "application/pdf"
            :style {:width  "100%"
                    :height "calc(100vh - 102px)"}}
   [:a {:href pdf-src}
    "Download facsimile"]])

(defn nth-document!
  [n]
  (let [{:keys [results]} @state/search
        {:keys [limit]} @state/query]
    (cond
      (>= n limit)
      (do
        (swap! state/query update :offset + limit)
        (search-page/fetch-results!
          {:query-params (search-page/state->params @state/query)}
          #(nth-document! 0)))

      (< n 0)
      (do
        (swap! state/query update :offset - limit)
        (search-page/fetch-results!
          {:query-params (search-page/state->params @state/query)}
          #(nth-document! (dec limit))))

      :else
      (do
        (swap! state/search assoc :i n)
        (rfe/push-state ::page {:document (:file/name (nth results n))})))))

;; TODO: redo the .search-result__paging CSS now that it fills two roles
(defn reader-paging
  [tr results i {:keys [offset limit] :as query-state}]
  [:div.search-result__paging
   [:div.input-row
    [:button {:disabled (= i offset 0)
              :on-click #(nth-document! (dec i))}
     [tr ::search-page/prev]]
    [:select {:on-change #(nth-document! (js/parseInt (.-value (.-target %))))
              :value     i}
     (when (> offset 0)
       [:option {:value -1}
        (tr ::prev-results)])
     (for [entity results
           :let [n    (:i (meta entity))
                 file (:file/name entity)]]
       [:option {:key   file
                 :value n}
        file])
     (when (< (+ offset limit) (:total (meta results)))
       [:option {:value (+ offset limit)}
        (tr ::next-results)])]
    [:button {:disabled (= (dec (:total (meta results)))
                           (+ i offset))
              :on-click #(nth-document! (inc i))}
     [tr ::search-page/next]]]])

(defn page
  []
  (let [{:keys [hiccup tei document entity]} @state/reader
        {:keys [id->name results i] :as search-state} @state/search
        query-state        @state/query
        tr                 (i18n/->tr)
        location*          @state/location
        current-document   (get-in location* [:path-params :document])
        local-preview?     (empty? current-document)
        document-selected? (= ::page (get-in location* [:data :name]))
        new-document?      (not= document current-document)
        {:keys [document/condition document/facsimile]} entity
        body?              (get condition "transcribed")
        paging?            (and i (> (count results) 1))
        pdf-src            (and (not body?)
                                (string? facsimile)
                                (str/ends-with? facsimile ".pdf")
                                (fshared/backend-url (str "/file/" facsimile)))]

    ;; Uses a side-effect of the rendering function to load new documents.
    ;; Probably a bad way to do this...
    (when (and (not local-preview?)
               document-selected?
               new-document?
               (not= current-document *current-fetch*))
      (set-content! current-document))

    [:article {:class (if local-preview?
                        "reader-preview-page"
                        "reader-page")}
     (when local-preview?
       [:input {:aria-label (tr ::local-file)
                :type       "file"
                :on-change  (fn [e]
                              (when-let [file (.item e.target.files 0)]
                                (.then (.text file)
                                       (fn [s]
                                         (set-content! (.-name file) s)))))}])

     (when paging?
       [reader-paging tr results i query-state])

     (when hiccup
       (if local-preview?
         ;; Only for previewing TEI parsing functionality.
         [pattern/tabs
          {:i   0
           :kvs (pattern/heterostyled
                  [[[tr ::transcription]
                    ^{:key tei} [rescope/scope hiccup tei-css]]

                   ["Metadata"
                    (when id->name
                      [:div.reader-content
                       [entity-meta tr search-state entity]])]

                   ["TEI"
                    [:pre.reader-content
                     [:code {:style {:white-space "pre-wrap"}}
                      tei]]]])}

          {:id "tei-tabs"}]

         ;; The primary page, displaying data fetched from the server.
         [group/combination {:weights [1 1]}
          (if pdf-src
            [pdf-object pdf-src]
            [pattern/carousel state/facs-carousel])
          [pattern/tabs
           {:i   0
            :kvs (pattern/heterostyled
                   (cond->> [["Metadata"
                              (when id->name
                                [:div.reader-content
                                 [entity-meta tr search-state entity]])]

                             ["TEI"
                              [:pre.reader-content
                               [:code {:style {:white-space "pre-wrap"}}
                                tei]]]]

                            body?
                            (into
                              [[[tr ::transcription]
                                ^{:key tei} [rescope/scope hiccup tei-css]]])))}
           {:id "tei-tabs"}]]))

     (when paging?
       [reader-paging tr results i query-state])]))
