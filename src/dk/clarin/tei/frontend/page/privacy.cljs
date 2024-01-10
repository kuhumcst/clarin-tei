(ns dk.clarin.tei.frontend.page.privacy
  (:require [dk.clarin.tei.frontend.i18n :as i18n]
            [dk.clarin.tei.frontend :as-alias frontend]))

(defn page
  []
  (let [tr (i18n/->tr)]
    [:article.main-page                                         ;TODO: generic container
     [:h2 [tr ::frontend/privacy]]
     [tr ::text]]))
