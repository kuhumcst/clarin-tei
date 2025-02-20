(ns dk.clarin.tei.frontend.page.reader
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
            [dk.clarin.tei.shared :as shared]
            [dk.cst.stucco.pattern :as pattern]
            [dk.cst.stucco.group :as group]
            [dk.cst.stucco.util.css :as css]
            [dk.clarin.tei.db.tei :as db.tei]
            [dk.clarin.tei.frontend.page.search :as search-page]
            [dk.clarin.tei.frontend.state :as state :refer [db]]
            [dk.clarin.tei.frontend.api :as api]
            [dk.clarin.tei.frontend.shared :as fshared]
            [dk.clarin.tei.static-data :as sd]
            [dk.clarin.tei.frontend.i18n :as i18n]))

(def tei-css
  "Styles used for TEI documents specifically. They are written in a regular CSS
  file and then processed to work on the generated HTML."
  (style/prefix-css "tei" (resource/inline "dk/clarin/tei/public/css/tei.css")))

(def theme+tei-css
  "The complete set of styles (widgets and TEI documents)."
  (str css/shadow-style "\n\n/*\n\t === tei.css ===\n*/\n" tei-css))

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

(def merge-strings
  (cup/->transformer
    '[_ {} ???]

    (fn [m]
      (let [[tag attr & content] (:source (meta m))]
        (into [tag attr] (shared/merge-strings content))))))

(declare inner-stage)

;; Unlike the 'outer-stage', the 'inner-stage' transformations can be safely
;; memoised since they don't have any side-effects.
(def rewrite-inner
  (memoize #(cup/rewrite % inner-stage)))

(def lb-as-br
  (cup/->transformer '[:lb] '[:br]))

(def list-as-ul
  (cup/->transformer
    '[:list (... list-items)]

    (fn [{:syms [list-items]}]
      (into [:ul] (for [list-item list-items]
                    (let [[tag attr & content] (rewrite-inner list-item)]
                      (when (= tag :tei-item)               ; safeguard
                        (into [:li] content))))))))

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

(defn- cb?
  [x]
  (and (vector? x)
       (= :cb (first x))))

(defn- fw?
  [x]
  (and (vector? x)
       (= :fw (first x))))

(defn update-tei-carousel!
  "Updates the `carousel-state` when new `kvs` are detected."
  [carousel-state kvs]
  (let [old-kvs (:kvs @carousel-state)]
    (when (not= (map first kvs) (map first old-kvs))
      (reset! carousel-state {:i 0 :kvs kvs}))))

(defn paginate
  "Paginate the following `nodes` every time (pred node) is true for `pred`."
  [pred container nodes]
  (->> (drop-while (complement pred) nodes)                 ; pre-page content
       (reduce
         (fn [pages node]
           (if (pred node)
             (conj pages (conj container node))
             (update pages (dec (count pages)) conj node)))
         [])))

(defn body->pages
  "Divide the Hiccup `body` into pages for every [:pb]."
  [body]
  (->> (ht/split-hiccup pb? body :retain :between)
       (ht/node-children)
       (drop-while (complement pb?))
       (paginate pb? [:page])))

(defn with-columns
  "If the `page` contains any columns marked with <cb>, it will be structurally
  altered to reflect this fact."
  [[page-tag pb & content :as page]]
  (let [page' (into [page-tag] content)
        split (ht/split-hiccup cb? page' :retain :between)]
    (if (= page' split)
      page                                                  ; for performance
      (let [[pre-column column-content] (->> (ht/node-children split)
                                             (split-with (complement cb?)))
            columns      (paginate cb? [:column] column-content)
            last-column' (ht/cut fw? (last columns))
            matches      (:matches (meta last-column'))]

        ;; Any pre-column (i.e. non-column) content is always added first.
        (cond-> (into [page-tag pb] pre-column)

                ;; If the page has columns, these are inserted, but with any
                ;; potential <fw> elements removed from the final column.
                column-content
                (conj (into [:columns] (if matches
                                         (conj (subvec columns 0 (dec (count columns)))
                                               (with-meta last-column' {}))
                                         columns)))

                ;; Any <fw> elements found in the final comment are added as
                ;; post-column content.
                matches
                (into matches))))))

;; Fairly complex transformer that restructures sibling-level page content into
;; a stucco carousel component. The large amount of content captured as page
;; content has to be explicitly rewritten in a separate call; otherwise, it will
;; be skipped entirely.
(def pages-in-carousel
  "Get a transformer for arranging a TEI document into pages."
  (cup/->transformer
    '[:body (... content)]

    (fn [m]
      (let [pages (map with-columns (body->pages (:source (meta m))))
            kvs   (for [[_ [_ {:keys [n facs]}] :as page] pages]
                    [nil
                     (rewrite-inner page)])]
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
  {:transformers [lb-as-br
                  list-as-ul
                  date-as-time]
   :wrapper      shadow-dom-wrapper
   :default      default-fn})

;; Note that this step *cannot* be memoised since the document rendering relies
;; on side-effects executed inside the 'pages-in-carousel' transformation.
(def outer-stage
  "Places all TEI pages inside a carousel component in a shadow DOM."
  {:transformers [pages-in-carousel]
   :wrapper      shadow-dom-wrapper
   :default      default-fn})

(def parse
  (memoize xml/parse))

(defn- assert-alt
  "Assert from the `attr` that the image has an alt text."
  [{:keys [src alt] :as attr}]
  (assert alt
          (str src " lacks an alt text: " attr)))

(defn illustration
  "Illustration that can be full-screened if need be. Replaces [:img]."
  [{:keys [src] :as attr}]
  (assert-alt attr)
  [:a.illustration {:href src}
   [:img attr]])

(defn facs-id->facs-page
  [tr id]
  (let [tif-url (fshared/backend-url (str "/file/" id))
        jpg-url (str tif-url ".jpg")]
    [[:a {:href jpg-url} (str id ".jpg")]
     [illustration {:src jpg-url
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
  This feature is used to preview local TEI documents in the browser."
  [document & [xml]]
  (set! *current-fetch* document)
  ;; Should not display old state while waiting for the network request.
  (when (not= document (:document @state/reader))
    (swap! state/reader assoc :i 0 :document nil))

  ;; TODO: fix :tei-kvs side-effect, makes it hard to implement history/cache
  (p/let [tei              (or xml (api/fetch (str "/file/" document)))
          clean-tei        (db.tei/remove-oxygen-declaration tei)
          entity           (if xml
                             (-> (db.tei/->triples document xml)
                                 (db.tei/triples->entity))
                             (api/fetch (str "/entity/" document)))
          raw-hiccup       (parse clean-tei)
          tr               (i18n/->tr)
          facs             (->> (normalize-facs (:document/facsimile entity))
                                (mapv (partial facs-id->facs-page tr)))
          rewritten-hiccup (cup/rewrite raw-hiccup pre-stage merge-stage outer-stage)
          tei-kvs          (:kvs @state/tei-carousel)
          missing-count    (- (count facs) (count tei-kvs))
          placeholder      (tr ::placeholder)]
    (swap! state/reader assoc

           :document document
           :entity entity
           :tei clean-tei
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

(defn page
  []
  (let [{:keys [hiccup tei document entity]} @state/reader
        {:keys [id->name] :as search-state} @state/search
        tr                 (i18n/->tr)
        location*          @state/location
        current-document   (get-in location* [:path-params :document])
        local-preview?     (empty? current-document)
        document-selected? (= ::page (get-in location* [:data :name]))
        new-document?      (not= document current-document)
        {:keys [document/condition document/facsimile]} entity
        body?              (= condition "transcribed")
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
     (when hiccup
       (let [transcription [pattern/tabs
                            {:i   0
                             :kvs [[[tr ::transcription]
                                    ^{:key tei} [rescope/scope hiccup tei-css]]

                                   ^{:style {:background "var(--flexoki-magenta-400)"
                                             :color      "white"}}
                                   ["Metadata"
                                    (when id->name
                                      [:section.text-content
                                       [entity-meta tr search-state entity]])]]}

                            {:id "tei-tabs"}]]
         (if local-preview?
           [:<>
            [:input {:aria-label (tr ::local-file)
                     :type       "file"
                     :on-change  (fn [e]
                                   (when-let [file (.item e.target.files 0)]
                                     (.then (.text file)
                                            (fn [s]
                                              (set-content! (.-name file) s)))))}]
            transcription]
           [group/combination {:weights [35 65]}
            (if pdf-src
              [pdf-object pdf-src]
              [pattern/carousel state/facs-carousel])
            transcription])))]))
