(ns dk.clarin.tei.frontend.page.bookmarks
  (:require [dk.clarin.tei.frontend :as-alias frontend]
            [dk.clarin.tei.frontend.shared :as fshared]
            [dk.clarin.tei.frontend.state :as state]
            [dk.clarin.tei.frontend.i18n :as i18n]
            [dk.clarin.tei.frontend.page.search :as-alias search]
            [dk.clarin.tei.frontend.page.reader :as-alias reader]
            [dk.clarin.tei.frontend.api :as api]
            [dk.clarin.tei.shared :as shared]))

(defn index-links
  [groups]
  (into [:p.index-links]
        (->> groups
             (map (fn [[year]]
                    (let [fragment (str "#" (fshared/legal-id year))]
                      [:a {:href     fragment
                           :on-click #(fshared/find-fragment fragment)}
                       year])))
             (interpose " / ")
             (vec))))

(defn bookmarks-content
  [tr bookmarks]
  (let [user (shared/assertions->user-id state/assertions)]
    [:ul.bookmarks
     (for [{:keys [db/ident
                   bookmark/path
                   bookmark/title]} bookmarks]
       [:li {:key path}
        [:button {:title    (tr ::frontend/rem-bookmark-caption)
                  :on-click #(api/del-bookmark user path ident)}]
        [:a {:title (tr ::go-caption)
             :href  path}
         (or title path)]])]))

(def categories
  {::search/page ::searches
   ::reader/page ::documents})

(defn page
  []
  (let [bookmarks @state/bookmarks
        tr        (i18n/->tr)
        author    (-> @state/location :path-params :author)]
    [:article.index-page
     [:h1
      [:img {:src "/images/book-fill.svg"}] " " (tr ::frontend/bookmarks)]
     (if (empty? bookmarks)
       [:div.text-content.menu
        [:p (tr ::empty)]]
       (let [groups (as-> bookmarks $
                          (map (fn [[path bookmark]]
                                 (assoc bookmark :bookmark/path path)) $)
                          (sort-by :entity/edited bookmarks $)
                          (group-by (comp categories :bookmark/page) $)
                          (update-keys $ (fnil tr ::other))
                          (sort-by key $))]
         [:<>
          [:div.text-content.menu
           [index-links groups]]
          ^{:key author}
          [fshared/kvs-list groups (partial bookmarks-content tr)]]))]))
