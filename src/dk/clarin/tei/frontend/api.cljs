(ns dk.clarin.tei.frontend.api
  "Common API access operations."
  (:require [lambdaisland.fetch :as fetch]
            [kitchen-async.promise :as p]
            [dk.clarin.tei.frontend.state :as state]
            [dk.clarin.tei.frontend.shared :as fshared]))

(defn- refresh-dialog-msg
  [status]
  (str "Cannot fetch necessary data from the server. "
       (case status
         403 "The resource is restricted — you may have been logged out!\n\n"
         404 "The resource does not exist — the page may need to refresh!\n\n"
         500 "The server responded with an error!\n\n"
         nil)
       "Do you want to log in again and try to refresh the page?\n\n"))

(defn refresh-dialog
  "Display a dialog based on the HTTP `status` asking to refresh the page."
  [status]
  (when-not state/*block-modal-dialogs*
    (set! state/*block-modal-dialogs* true)
    (if (js/confirm (refresh-dialog-msg status))
      (js/location.reload)
      (js/history.back))))

(defn fetch
  "Do a GET request for the resource at `url`, returning the response body.
  Bad response codes result in a dialog asking the user to log in again.

  Usually, bad responses (e.g. 403) are caused by frontend-server mismatch
  which can be resolved by loading the latest version of the frontend app."
  [url & [opts]]
  (let [url'          (fshared/backend-url url)
        current-fetch [url' opts]]
    (when-not (get @state/fetches current-fetch)
      (swap! state/fetches conj current-fetch)
      (p/let [{:keys [status body] :as m} (fetch/get url' opts)]
        (swap! state/fetches disj current-fetch)
        (if-not (<= 200 status 299)
          (refresh-dialog status)
          body)))))
