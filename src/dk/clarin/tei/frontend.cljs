(ns dk.clarin.tei.frontend
  "The central namespace of the frontend client; defines frontend routing."
  (:require [clojure.string :as str]
            [dk.clarin.tei.static-data :as sd]
            [reagent.dom :as rdom]
            [reagent.cookies :as cookie]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe :refer [href]]
            [time-literals.read-write :as tl]
            [dk.cst.stucco.util.css :as css]
            [dk.clarin.tei.frontend.i18n :as i18n]
            [dk.clarin.tei.frontend.shared :as fshared]
            [dk.clarin.tei.frontend.state :as state :refer [db]]
            [dk.clarin.tei.frontend.page.privacy :as privacy]
            [dk.clarin.tei.frontend.page.search :as search]
            [dk.clarin.tei.frontend.page.reader :as reader]))

(def routes
  [["/tei/app/privacy"
    {:name  ::privacy/page
     :title ::privacy
     :page  privacy/page}]
   ["/tei/app/search"
    {:name  ::search/page
     :title search/page-title
     :page  search/page
     :prep  #(search/fetch-results!
               % (fn []
                   (do
                     (search/?query-reset!)
                     (fshared/set-title! (search/page-title)))))}]
   ["/tei/app/search/:kind"
    {:name  ::search/index-page
     :title (fn [m]
              (search/?query-reset!)
              (->> (get-in m [:path-params :kind])
                   (keyword "entity.type")
                   ((i18n/->tr))))
     :page  search/page}]
   ["/tei/app/reader"
    {:name  ::reader/preview
     :title ::local-reader
     :page  reader/page}]
   ["/tei/app/reader/:document"
    {:name  ::reader/page
     :title (fn [m]
              (get-in m [:path-params :document]))
     :page  reader/page}]])

(def lang
  {"da" "en"
   "en" "da"})

(def year-in-seconds
  (* 60 60 12 365))

(def cookie-opts
  {:max-age year-in-seconds
   :path    "/"
   :raw?    true                                            ; use raw strings
   :secure? true})

(defn index-links
  [tr & [current-type]]
  (->> (sort-by (comp tr first) sd/real-entity-types)
       (map (fn [[entity-type {:keys [img-src]}]]
              (if (= current-type entity-type)
                [:span (tr entity-type)]
                [:a {:href     (fshared/index-href entity-type)
                     :disabled (= current-type entity-type)}
                 (tr entity-type)])))
       (into [:nav.index-links])))

(defn skip-links
  [groups]
  (into [:nav.skip-links]
        (->> groups
             (map (fn [[letter]]
                    (let [fragment (str "#" (fshared/legal-id letter))]
                      [:a {:href     fragment
                           :on-click #(fshared/find-fragment fragment)}
                       letter])))
             (interpose ", ")
             (vec))))

(defn shell
  "A container component that wraps the various pages of the app."
  []
  (let [loc       @state/location
        {:keys [metadata]} @state/search
        {:keys [page name]} (:data loc)
        fetching? (not-empty @state/fetches)
        tr        (i18n/->tr)]
    ;; A containing div is currently needed for the timeline to work properly.
    [:div#shell {:class [(when fetching?
                           "fetching")]}
     (if (= name ::reader/page)
       ;; Special header used on the reader page to save vertical space.
       ;; It either simulates the back button or goes to a blank search page.
       [:button.back {:on-click #(if (-> loc :prev)
                                   (js/history.back)
                                   (rfe/navigate ::search/page))}
        (tr ::back)]

       ;; The primary header that is used on every other page but the reader.
       [:header
        [:section.top
         [:h1
          [:a {:href  (href ::search/page)
               :title (tr ::main-caption)}
           "CLARIN TEI"]
          [:button.language {:title    (tr ::language-caption)
                             :on-click (fn [_]
                                         (let [v (swap! state/language lang "da")]
                                           (cookie/set! :language v cookie-opts)
                                           (-> @state/location
                                               (fshared/location->page-title)
                                               (fshared/set-title!))))}
           (tr ::language-flag)]]
         [:aside
          [:nav
           [:a {:href "https://github.com/kuhumcst/clarin-tei"}
            "Github"]
           [:a {:href "/tei/app/privacy"}
            (tr ::privacy)]
           [:a {:href "https://www.was.digst.dk/clarin-dk"}
            (tr ::a11y)]]]]
        [:section.middle
         [index-links tr (fshared/current-index)]]
        (when metadata
          (when-let [entity-type (fshared/current-index)]
            [:section.bottom
             [skip-links (fshared/index-groups metadata entity-type)]]))])

     ;; The actual, page-specific content.
     [:main
      (if page
        [page]
        [tr ::unknown-page])]

     [:footer
      [tr ::copyright]]]))

(defn universal-prep!
  "Prepare widely needed state."
  []
  (let [{:keys [name->id]} @state/search]
    (when-not name->id
      (search/fetch-metadata!))))

(defn on-navigate
  [{:keys [path query-params] :as m}]
  (let [prev-loc @state/location]
    ;; Avoid re-fetching/resetting on soft reloads, e.g. by shadow-cljs.
    (when (or (not= path (:path prev-loc))
              (not= query-params (:query-params prev-loc)))
      (set! state/*block-modal-dialogs* false)
      (universal-prep!)
      (when-let [prep (get-in m [:data :prep])]
        (prep m)))

    ;; We retain a link to the previous location to allow for deciding between
    ;; simulating the back button or going to the main page.
    (reset! state/location (assoc m :prev prev-loc))
    (fshared/set-title! (fshared/location->page-title m))

    ;; Scroll state is always reset when no intra-page navigation is expected.
    (when (empty? js/window.location.hash)
      (fshared/scroll-reset! "body"))))

(defn ^:dev/after-load render
  []
  (rfe/start! (rf/router routes) on-navigate {:use-fragment false})
  (rdom/render [shell] (js/document.getElementById "app")))

(defn init!
  "The entry point of the frontend app."
  []
  ;; Make sure that edn/read-string can process timestamp literals
  (tl/print-time-literals-cljs!)

  (let [style      (js/document.createElement "style")
        root-style (str/replace-first css/shadow-style ":host" ":root")]
    (set! (.-innerHTML style) root-style)
    (js/document.head.appendChild style))

  (render))
