(ns dk.clarin.tei.frontend.page.index
  "Page containing a range of indices of important entities."
  (:require [clojure.string :as str]
            [dk.clarin.tei.static-data :as sd]
            [dk.clarin.tei.frontend.i18n :as i18n]
            [dk.clarin.tei.frontend.shared :as fshared]
            [dk.clarin.tei.frontend.state :as state]
            [dk.clarin.tei.shared :as shared]))

(defn str->index-group
  "The canonical index group for a given `s`; used for group-by."
  [s]
  (or
    (when-let [initial-num (re-find #"^\d+" s)]
      initial-num)
    (when s
      (first (str/upper-case (fshared/str-sort-val s))))))

(defn- index-groups
  [search-metadata entity-type]
  (let [entities (get search-metadata entity-type)]
    (->> (if (= entity-type :entity.type/person)
           (map (fn [[k v]]
                  (let [k' (fshared/surname-first k)]
                    [(if (str/ends-with? k' ", ") k k') v]))
                entities)
           entities)
         (group-by (comp str->index-group first))
         (remove (comp nil? first))
         (sort-by first)
         (into []))))

(defn index-links
  [tr & [current-type]]
  (->> (sort-by (comp tr first) sd/real-entity-types)
       (map (fn [[entity-type {:keys [img-src]}]]
              (if (= current-type entity-type)
                [:span [:img.entity-icon {:src img-src}]
                 (tr entity-type)]
                [:a {:href     (fshared/index-href entity-type)
                     :disabled (= current-type entity-type)}
                 [:img.entity-icon {:src img-src}]
                 (tr entity-type)])))
       (interpose " / ")
       (into [:p.index-links])))

(defn skip-links
  [groups]
  (into [:p.index-page__skip-links]
        (->> groups
             (map (fn [[letter]]
                    (let [fragment (str "#" (fshared/legal-id letter))]
                      [:a {:href     fragment
                           :on-click #(fshared/find-fragment fragment)}
                       letter])))
             (interpose ", ")
             (vec))))

(defn index-content
  [kvs]
  [:ul
   (for [[k v] (sort-by (comp fshared/str-sort-val first) kvs)]
     [:li {:key k}
      [:a {:href (fshared/search-href v)}
       (shared/local-name (str k))]])])

(defn page
  []
  (let [{:keys [metadata]} @state/search
        tr          (i18n/->tr)
        entity-type (->> (get-in @state/location [:path-params :kind])
                         (keyword "entity.type"))
        {:keys [img-src]} (get sd/real-entity-types entity-type)]
    [:article.index-page
     [:h1 [:img {:src img-src}] " " (tr entity-type)]
     (when metadata
       (let [groups (index-groups metadata entity-type)]
         [:<>
          [:div.text-content.menu
           [index-links tr entity-type]
           [:hr]
           [skip-links groups]]
          ^{:key groups} [fshared/kvs-list groups index-content]]))]))
