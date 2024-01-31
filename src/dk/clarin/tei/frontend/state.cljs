(ns dk.clarin.tei.frontend.state
  "Contains both static and dynamic frontend state."
  (:require [reagent.core :as r]
            [reagent.cookies :as cookie]
            [clojure.edn :as edn]
            [dk.cst.stucco.util.state :as su]))

(defonce development?
  (when (exists? js/inDevelopmentEnvironment)
    js/inDevelopmentEnvironment))

(defonce proxy-prefix
  (when (exists? js/proxyPrefix)
    js/proxyPrefix))

(defonce language
  (r/atom
    (if-let [previously-specified (cookie/get-raw :language)]
      previously-specified
      (if (exists? js/negotiatedLanguage)
        (:type (edn/read-string js/negotiatedLanguage))
        "en"))))

(defonce fetches
  (r/atom #{}))

(defn proxied
  [url]
  (str proxy-prefix url))

;; To avoid having multiple modals in succession after multiple bad API fetches,
;; additional modals will be blocked until the route changes.
(def ^:dynamic *block-modal-dialogs*)

(def local-query-keys
  "Keys used locally by the search page; not transferable via query-params."
  #{:in :n :bad-input? :not-allowed?})

(def query-result-set-keys
  "Keys that only affect the size/order of the query result set.

  NOTE: same keys are used for both the query state and the query-params."
  #{:limit :offset :order-by :from :to})

(def query-defaults
  {:unique   #{} :items []                                  ; = an ordered set
   :n        0                                              ; background index
   :in       ""                                             ; input text
   :limit    10
   :offset   0
   :order-by [nil :asc]
   :from     nil
   :to       nil})

;; Local page data should all be cursors into this central data structure.
(defonce db
  (r/atom {:reader    nil
           :search    {:query query-defaults}}))

(defonce location
  (r/cursor db [:location]))

(defonce ui
  (r/cursor db [:ui]))

(defonce search
  (r/cursor db [:search :meta]))

(defonce query
  (r/cursor db [:search :query]))

(defonce reader
  (r/cursor db [:reader]))

(defonce facs-carousel
  (su/ghost reader {:i        :i
                    :facs-kvs :kvs}))

(defonce tei-carousel
  (su/ghost reader {:i       :i
                    :tei-kvs :kvs}))
