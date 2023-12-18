(ns dk.clarin.tei.frontend
  "The central namespace of the frontend client; defines frontend routing."
  (:require [clojure.string :as str]
            [cljs.pprint :refer [pprint]]
            [reagent.dom :as rdom]
            [reagent.cookies :as cookie]
            [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe :refer [href]]
            [time-literals.read-write :as tl]
            [dk.cst.stucco.util.css :as css]
            [dk.cst.pedestal.sp.auth :as sp.auth]
            [dk.clarin.tei.shared :as shared]
            [dk.clarin.tei.frontend.i18n :as i18n]
            [dk.clarin.tei.frontend.shared :as fshared]
            [dk.clarin.tei.frontend.state :as state :refer [db]]
            [dk.clarin.tei.frontend.api :as api]
            [dk.clarin.tei.frontend.page.main :as main]
            [dk.clarin.tei.frontend.page.privacy :as privacy]
            [dk.clarin.tei.frontend.page.search :as search]
            [dk.clarin.tei.frontend.page.bookmarks :as bookmarks]
            [dk.clarin.tei.frontend.page.index :as index]
            [dk.clarin.tei.frontend.page.reader :as reader]))

(def routes
  [["/tei/privacy"
    {:name  ::privacy/page
     :title ::privacy
     :page  privacy/page}]
   ["/tei/search"
    {:name  ::search/page
     :title search/page-title
     :page  search/page
     :prep  #(search/fetch-results!
               % (fn []
                   (do
                     (search/?query-reset!)
                     (fshared/set-title! (search/page-title)))))}]
   ["/tei/bookmarks"
    {:name  ::bookmarks/page
     :title ::bookmarks
     :page  bookmarks/page}]
   ["/tei/index/:kind"
    {:name  ::index/page
     :title (fn [m]
              (->> (get-in m [:path-params :kind])
                   (keyword "entity.type")
                   ((i18n/->tr))))
     :page  index/page}]
   ["/tei/reader"
    {:name  ::reader/preview
     :title ::local-reader
     :page  reader/page}]
   ["/tei/reader/:document"
    {:name  ::reader/page
     :title (fn [m]
              (get-in m [:path-params :document]))
     :page  reader/page}]])

;; TODO: remove...?
(defn debug-view
  []
  [:details {:style {:opacity "0.33"}} [:summary "DEBUG"]
   [:details [:summary "auth"]
    (sp.auth/if-permit [state/assertions {:attrs {"firstName" #{"Simon"}}}]
      "firstName = Simon"
      "firstName != Simon")]
   [:details [:summary "assertions"]
    [:pre (with-out-str (pprint state/assertions))]]
   [:details [:summary "@db"]
    [:pre (with-out-str (pprint @db))]]])

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

(defn mark-first
  "Mark the first character of a string `s` with the class first.

  This annoying workaround is needed because of 15+ year old bug in Firefox:
  https://bugzilla.mozilla.org/show_bug.cgi?id=385615"
  [s]
  (let [c (subs s 0 1)
        s (subs s 1)]
    [:<> [:span.first c] s]))

(defn shell
  "A container component that wraps the various pages of the app."
  []
  (let [{:keys [page name]} (:data @state/location)
        #_#_path           (fshared/current-path)
        #_#_authenticated? @state/authenticated?
        #_#_user           (shared/assertions->user-id state/assertions)
        #_#_{:keys [db/ident] :as bookmark} (get @state/bookmarks path)
        fetching?      (not-empty @state/fetches)
        tr             (i18n/->tr)]
    ;; A containing div is currently needed for the timeline to work properly.
    [:div#shell {:class [(when fetching?
                           "fetching")]}
     [:header
      [:h1
       [:a {:href  (href ::search/page)
            :title (tr ::main-caption)}
        "TEI reader"]
       [:button.language {:title    (tr ::language-caption)
                          :on-click (fn [_]
                                      (let [v (swap! state/language lang "da")]
                                        (cookie/set! :language v cookie-opts)
                                        (-> @state/location
                                            (fshared/location->page-title)
                                            (fshared/set-title!))))}
        (tr ::language-flag)]]
      [:nav
       #_[:input.bookmark {:type      "checkbox"
                           :disabled  (not authenticated?)
                           :checked   (boolean bookmark)
                           :title     (if bookmark
                                        (tr ::rem-bookmark-caption)
                                        (tr ::add-bookmark-caption))
                           :on-change (fn [e]
                                        (.preventDefault e)
                                        (if bookmark
                                          (api/del-bookmark user path ident)
                                          (api/add-bookmark user path name)))}]]]
     [:main
      [:img.loading-indicator {:alt ""                      ; signal decorative
                               :src "/images/loading.svg"}]
      (if page
        [page]
        [tr ::unknown-page])]
     [:footer
      [:section.links
       [:a {:href "https://www.was.digst.dk/glossematics-dk"}
        (mark-first (tr ::a11y))]
       [:span " / "]
       [:a {:href "/tei/privacy"}
        (mark-first (tr ::privacy))]
       [:span " / "]
       [:a {:href "https://github.com/kuhumcst/clarin-tei"}
        (mark-first "Github")]]
      [:section.links
       [tr ::copyright]]]]))

(defn fetch-bookmarks!
  "Fetches and post-processes metadata used to populate the search form."
  [user]
  (.then (api/fetch (str "/user/" user "/bookmarks"))
         (fn [bookmarks]
           (reset! state/bookmarks bookmarks))))

(defn universal-prep!
  "Prepare widely needed state."
  []
  (let [{:keys [name->id]} @state/search
        bookmarks @state/bookmarks]
    #_(when (and (not bookmarks) state/assertions)
        (when-let [user (shared/assertions->user-id state/assertions)]
          (fetch-bookmarks! user)))
    (when-not name->id
      (search/fetch-metadata!))))

(defn on-navigate
  [{:keys [path query-params] :as m}]
  (let [old-location @state/location]
    ;; Avoid re-fetching/resetting on soft reloads, e.g. by shadow-cljs.
    (when (or (not= path (:path old-location))
              (not= query-params (:query-params old-location)))
      (set! state/*block-modal-dialogs* false)
      (universal-prep!)
      (when-let [prep (get-in m [:data :prep])]
        (prep m)))

    (reset! state/location m)
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

  ;; Needed for debugging login process
  (println "SAML assertions:")
  (prn state/assertions)

  (render))
