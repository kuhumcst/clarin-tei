(ns dk.cst.glossematics.frontend.shared
  "Frontend code that can be freely shared between frontend namespaces."
  (:require [clojure.string :as str]
            [reitit.frontend.easy :as rfe]
            [tick.core :as t]
            [dk.cst.glossematics.frontend.state :as state]
            [dk.cst.glossematics.static-data :as sd]
            [dk.cst.stucco.pattern :as stp]))

(defn surname-listing
  "Put the surname of `s` at the front, followed by a comma and the rest."
  [s]
  (let [parts (str/split s #" ")]
    (apply str (last parts) ", " (str/join " " (butlast parts)))))

(defn str-sort-val
  "Remove prepended parentheses from `s`."
  [s]
  (-> s
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

;; TODO: eventually use :as-alias
(defn encyclopedia-href
  [ref]
  (rfe/href :dk.cst.glossematics.frontend.page.search.encyclopedia/entry
            {:ref (if (str/starts-with? ref "#")
                    (subs ref 1)
                    ref)}))

;; TODO: eventually use :as-alias
(defn search-href
  [ref]
  (rfe/href :dk.cst.glossematics.frontend.page.search/page {}
            (merge (select-keys state/query-defaults [:limit :offset])
                   {'_ ref})))

;; TODO: eventually use :as-alias
(defn index-href
  [entity-type]
  (rfe/href :dk.cst.glossematics.frontend.page.index/page
            {:kind (name entity-type)}))

;; TODO: eventually use :as-alias
(defn reader-href
  [document]
  (rfe/href :dk.cst.glossematics.frontend.page.reader/page
            {:document document}))

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

;; TODO: missing proper cycle http://localhost:9000/app/search?_=%23np57%2C%23np388&limit=10&offset=10
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
  [id->type coll]
  (group-by (if (inst? (first coll))
              (comp str t/year)
              (comp :entity-label sd/entity-types id->type))
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

(defn- metadata-table-val
  [{:keys [id->name id->type] :as search-state} k v]
  (let [into-ul (fn [coll]
                  (into [:ul]
                        (->> (sort-coll id->name coll)
                             (map #(metadata-table-val search-state k %))
                             (map #(vector :li %)))))]
    (cond
      ;; Hiccup passes unchanged
      (vector? v)
      v

      ;; Special behaviour.
      (= k :document/facsimile)
      (if (set? v)
        (str (count v) " pages")
        (str "1 page"))

      ;; Individual entities caught here.
      (and (string? v) (str/starts-with? v "#"))
      [:a {:href  (search-href v)
           :title "View in the reader"
           :key   v}
       (when-let [img-src (some-> v id->type sd/entity-types :img-src)]
         [:img.entity-icon {:src img-src :alt ""}])
       (get id->name v v)]

      ;; Collections caught here.
      (set? v)
      (into [:dl]
            (->> (group-coll id->type v)
                 (sort-by key)
                 (map (fn [[k coll]]
                        [:<>
                         [:dt k]
                         [:dd (into-ul coll)]]))))

      ;; :file/body?
      (boolean? v)
      (if v
        [:em "available"]
        [:span.weak "n/a"])

      (inst? v)
      (let [d   (.toISOString v)
            ret (str/split d #"T")]
        (if (coll? ret)
          (first ret)
          d))

      :else
      (str v))))

(defn metadata-table
  [search-state kvs]
  [:table.entity-metadata
   [:tbody
    (for [[k v] kvs]
      [:tr {:key k}
       [:td [:strong (str (get sd/rel->label k k))]]
       [:td (metadata-table-val search-state k v)]])]])

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
