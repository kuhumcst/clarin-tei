(ns dk.cst.glossematics.frontend.page.main
  (:require [dk.cst.glossematics.shared :as shared]
            [dk.cst.glossematics.frontend.state :as state]
            [dk.cst.glossematics.frontend.i18n :as i18n]
            [dk.cst.glossematics.frontend.api :as api]
            [clojure.string :as str]))

(defn user-section
  [tr]
  (let [institution (shared/assertions->institution state/assertions)
        assertions  (some-> (not-empty (:attrs state/assertions))
                            (dissoc "eduPersonTargetedID")  ; not needed
                            (->> (sort-by first)))]
    (if @state/authenticated?
      [:div.text-content.menu
       [:div.login-status
        (if institution
          [tr ::logged-in-status-1 institution]
          [tr ::logged-in-status])
        [:button.logout-button
         {:on-click (fn [e]
                      (.preventDefault e)
                      (api/logout))
          :title    (tr ::log-out-long)}
         [:span
          [:img {:src "/images/lock-svgrepo-com.svg" :alt ""}]
          [tr ::log-out]]]]
       (when (or assertions state/development?)
         [:<>
          [:hr]
          [:details
           [:summary [tr ::user-details]]
           [:aside
            [:table.entity-metadata
             [:tbody
              (for [[k v :as kv] assertions]
                [:tr {:key kv}
                 (into [:td] (for [c k]
                               (if (re-matches #"[A-Z]" c)
                                 [:<> [:wbr] c]
                                 c)))
                 [:td (->> v sort (str/join ", "))]])]]]]])]
      [:div.text-content.menu
       [:div.login-status
        [tr ::logged-out-status]
        [:button.login-button
         {:on-click (fn [e]
                      (.preventDefault e)
                      (api/login))
          :title    (tr ::log-in-long)}
         [:span
          [:img {:src "/images/unlock-svgrepo-com-modified.svg" :alt ""}]
          [tr ::log-in]]]]])))

(defn page
  []
  (let [{:keys [id->name]} @state/search
        tr (i18n/->tr)]
    [:article.main-page
     [user-section tr]
     [:div.text-content]]))
