(ns dk.cst.glossematics.frontend.shared
  "Frontend code that can be freely shared between frontend namespaces."
  (:require [clojure.string :as str]
            [reitit.frontend.easy :as rfe]
            [tick.core :as t]
            [dk.cst.glossematics.shared :as shared]
            [dk.cst.glossematics.frontend.state :as state]
            [dk.cst.glossematics.static-data :as sd]
            [dk.cst.glossematics.frontend.page.index :as-alias index]
            [dk.cst.glossematics.frontend.page.search :as-alias search]
            [dk.cst.glossematics.frontend.page.reader :as-alias reader]
            [dk.cst.stucco.pattern :as stp]
            [dk.cst.glossematics.frontend.i18n :as i18n]))

(defn backend-url
  [url]
  (if state/development?
    (str "http://localhost:8080" url)
    url))

(defn -surname-first
  [s]
  (let [parts (str/split s #" ")]
    (apply str (last parts) ", " (str/join " " (butlast parts)))))

(def surname-first
  "Put the surname of `s` in front, followed by a comma and the other parts."
  (memoize -surname-first))

(defn str-sort-val
  "Remove prepended parentheses from `s`."
  [s]
  (-> (str s)
      (str/replace #"^\(.+\)\s*" "")
      (str/replace #"^\-\s*" "")))

;; https://www.javascripttutorial.net/dom/css/check-if-an-element-is-visible-in-the-viewport/
(defn- visible?
  "Is the `element` fully located inside the browser's viewport?"
  [element]
  (let [rect (.getBoundingClientRect element)]
    (and (>= (.-top rect) (.-left rect) 0)
         (<= (.-bottom rect) (or js/window.innerHeight
                                 js/document.documentElement.clientHeight))
         (<= (.-right rect) (or js/window.innerWidth
                                js/document.documentElement.clientWidth)))))

(defn find-fragment
  "Scroll to the `fragment`; if none specified, read from window.location.hash."
  ([fragment]
   (when-let [elem (js/document.querySelector fragment)]
     (.scrollIntoView elem #js{:behavior "smooth"})))
  ([]
   (when-let [fragment (not-empty js/window.location.hash)]
     (find-fragment fragment))))

;; TODO: over-engineered? querySelector not really needed at the moment
(defn scroll-reset!
  "Scroll to the element defined by `query-selector`, e.g. when switching pages.

   Optionally set `account-for-header?` when needed!"
  [query-selector & [account-for-header?]]
  (let [top-elem      (js/document.querySelector query-selector)
        header-height (if account-for-header? 100 0)]
    (when-not (visible? top-elem)
      (.scrollIntoView top-elem true)
      (.scroll js/window 0 (+ js/window.scrollY header-height)))))

(defn search-href
  [ref]
  (rfe/href ::search/page {}
            (merge (select-keys state/query-defaults [:limit :offset])
                   {'_ ref})))

(defn index-href
  [entity-type]
  (rfe/href ::index/page {:kind (name entity-type)}))

(defn reader-href
  [document]
  (rfe/href ::reader/page {:document document}))

(defn legal-id
  "Make sure `s` is a legal HTML id/fragment, e.g. doesn't start with a number."
  [s]
  (let [s' (str/replace s #"[æøåÆØÅ]" sd/danish-letter->ascii)]
    (cond
      (not (re-matches #"[a-zA-Z0-9]+" s'))
      (str "X" (js/Math.abs (hash s')))

      (re-find #"^\d" s')
      (str "No" s')

      :else s')))

(def backgrounds
  (cycle stp/background-colours))

(defn- add-backgrounds
  [kvs offset]
  (stp/heterostyled kvs identity (if (number? offset)
                                   (drop offset backgrounds)
                                   backgrounds)))

(defn- sort-coll
  [id->name coll]
  (sort-by (if (inst? (first coll))
             identity
             (comp str-sort-val #(get id->name % %)))
           coll))

(defn- group-coll
  [id->type coll tr]
  (group-by (if (inst? (first coll))
              (comp str t/year)
              (comp tr id->type))
            coll))

(def break-str-xf
  "Transducer for annotating long Hiccup strings with word break opportunities."
  (let [sep? (partial re-matches #"_|\.")]
    (comp
      (partition-by sep?)
      (map (fn [cs]
             (if (and (= (count cs) 1)
                      (sep? (first cs)))
               [:<> [:wbr] (first cs)]
               (str/join cs)))))))

(defn break-str
  "Annotate `s` with word break opportunities."
  [s]
  (into [:<>] break-str-xf s))

(defn single
  "If `x` is a 'coll' return the first item; else return `x`."
  [x]
  (if (coll? x)
    (first (sort x))
    x))

(defn- handle-name
  "Ensures that `x` -- which can be either an ID, a name, or a set of either IDs
  or names -- parses correctly."
  [id->name x]
  (when x
    (let [id->name' #(get id->name % %)]
      (if (set? x)
        (->> (map id->name' x)
             (sort)
             (interpose "; ")
             (into [:<>]))
        (id->name' x)))))

(defn ?condition-tr
  "Translate the special :document/condition  `v` using `tr`."
  [tr v]
  (and (= tr i18n/tr-da)
       (get sd/en-attr->da-attr v)))

(defn- metadata-table-val
  "Create a Hiccup representation for `v` based on `k` and the source `m`;
  names are sourced via the `search-state`."
  [tr {:keys [id->name id->type] :as search-state} m k v]
  (let [into-ul (fn [coll]
                  (into [:ul]
                        (->> (sort-coll id->name coll)
                             (map #(metadata-table-val tr search-state m k %))
                             (map #(vector :li %)))))]
    (cond
      ;; Hiccup passes unchanged
      (vector? v)
      v

      ;; Special behaviour.
      (= k :document/facsimile)
      (if (set? v)
        (count v)
        1)

      (= k :file/name)
      [:a {:href     (backend-url (str "/file/" v))
           :download true}
       v]

      ;; Individual entities caught here.
      (and (string? v)
           (or (str/starts-with? v "#")
               (get sd/en-attr->da-attr v)))
      [:a {:href  (search-href v)
           :title (case k
                    :document/title (tr ::title-caption)
                    :document/condition (tr ::condition-caption)
                    (tr ::entity-caption))
           :key   v}
       (when-let [img-src (some-> v id->type sd/entity-types :img-src)]
         [:img.entity-icon {:src img-src :alt ""}])
       (if-let [attribute (?condition-tr tr v)]
         attribute
         (shared/local-name (get id->name v v)))]

      ;; Collections caught here.
      (set? v)
      (let [colls (group-coll id->type v tr)]
        (if (= 1 (count colls))
          (into-ul (second (first colls)))
          (into [:dl] (->> (sort-by key colls)
                           (map (fn [[k coll]]
                                  [:<>
                                   [:dt k]
                                   [:dd (into-ul coll)]]))))))

      ;; Dates are always imported as UTC and displayed as UTC; see: #69.
      ;; This is to account for the fact that there is not LocalDate type in JS.
      (inst? v)
      (let [d   (.toISOString v)
            ret (str/split d #"T")]
        (if (coll? ret)
          (first ret)
          d))

      :else
      (str v))))

(defn metadata-table
  [tr search-state m kvs]
  [:table.entity-metadata
   [:tbody
    (for [[k v] kvs]
      [:tr {:key k}
       [:td [:strong (str (tr k k))]]
       [:td (metadata-table-val tr search-state m k v)]])]])

(defn kvs-list
  "Generic display of title+content `kvs`; `val-com` renders the content."
  [kvs val-com & [offset]]
  [:dl.kvs-list {:ref #(find-fragment)}
   (for [[k v :as kv] (add-backgrounds kvs offset)]
     [:<> {:key k}
      [:dt {:id    (legal-id k)
            :style (:style (meta kv))}
       k]
      [:dd
       [val-com v]]])])

(defn current-path
  []
  (str js/location.pathname
       js/location.search
       js/location.hash))

(defn location->page-title
  "Generate a page title from the reitit on-navigate `location`; uses the
  specified title data for the given route."
  [location]
  (let [title (get-in location [:data :title])]
    (cond
      (string? title)
      title

      (keyword? title)
      ((i18n/->tr) title)

      (fn? title)
      (title location))))

(defn set-title!
  [title]
  (set! js/document.title (str (when state/development?
                                 "(dev) ")
                               title)))
