(ns dk.clarin.tei.backend
  "The central namespace of the backend app; defines backend routing."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [io.pedestal.log :as log]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [dk.clarin.tei.backend.shared :as bshared]
            [dk.clarin.tei.backend.index :as index]
            [dk.clarin.tei.backend.endpoints :as endpoints]
            [dk.clarin.tei.db :as db :refer [conn]]
            [dk.clarin.tei.db.search :as db.search])
  (:gen-class))

(defonce server (atom nil))
(defonce loaded-conf (atom nil))

(defn clarin-tei-routes
  "Most of the routing happens on the frontend inside the SPA; the API routes
   are the exception."
  [conf]
  (let [redirect-to-search [(fn [_] {:status  301
                                     :headers {"Location" "/tei/app/search"}})]
        spa-chain          [endpoints/lang-neg index/handler]]

    ;; These first few routes all lead to the SPA
    #{["/" :get redirect-to-search :route-name ::root]
      ["/tei" :get redirect-to-search :route-name ::tei]
      ["/tei/app" :get redirect-to-search :route-name ::spa]
      ["/tei/app/*" :get spa-chain :route-name ::spa-path]

      ;; API routes
      ["/tei/file/:filename"
       :get endpoints/file-chain
       :route-name ::endpoints/file]
      ["/tei/entity/:id"
       :get endpoints/entity-chain
       :route-name ::endpoints/entity]

      ;; Unrestricted at the route level, but performs local authorization.
      ;; Refer to the source code of the 'search-handler' for details.
      ["/tei/search"
       :get endpoints/search-chain
       :route-name ::endpoints/search]

      ;; The metadata contains names and internal IDs, but it doesn't contain
      ;; information about these names. It is needed to construct unrestricted
      ;; pages such as the bibliography page. Otherwise, that page would have to
      ;; be hidden. Under GDPR this will likely constitute legitimate interest.
      ["/tei/search/metadata"
       :get endpoints/search-metadata-chain
       :route-name ::endpoints/search-metadata]}))

(defn routes
  [conf]
  (route/expand-routes
    (clarin-tei-routes conf)))

(defn ->service-map
  [conf]
  (let [csp (if bshared/development?
              {:default-src "'self' 'unsafe-inline' 'unsafe-eval' localhost:* 0.0.0.0:* ws://localhost:* ws://0.0.0.0:*"
               :font-src    "'self' https://rsms.me/inter/"
               :style-src   "'self' 'unsafe-inline' https://rsms.me/inter/"}
              {:default-src "'self'"
               :base-uri    "'self'"
               :font-src    "'self' https://rsms.me/inter/"
               :script-src  "'self' 'unsafe-inline'"        ; TODO: unsafe-eval possibly only needed for dev main.js?
               :style-src   "'self' 'unsafe-inline' https://rsms.me/inter/"})]
    (cond-> {::http/routes         #((deref #'routes) conf)
             ::http/type           :jetty
             ::http/host           "0.0.0.0"                ; "localhost" won't work on a KU-IT server
             ::http/port           6789
             ::http/resource-path  "/dk/clarin"

             ;; Using the starter policy from https://content-security-policy.com/ as a basis
             ::http/secure-headers {:content-security-policy-settings csp}}

      ;; Make sure we can communicate with the Shadow CLJS app during dev.
      bshared/development? (assoc ::http/allowed-origins (constantly true)))))

(defn load-conf!
  "Load the config file at `conf-path`."
  [conf-path]
  (log/info :bootstrap.conf/load conf-path)
  (reset! loaded-conf (edn/read-string (slurp conf-path))))

(defn start-prod
  "Start a production environment using the config file at `conf-path`."
  [conf-path]
  (log/info :bootstrap/start {:dev? false})
  (let [conf        (load-conf! conf-path)
        service-map (->service-map conf)]
    (db/bootstrap! conf)
    (log/info :bootstrap.asami/begin-cache-names true)
    (->> (update-vals (db.search/search-metadata conn) count) ; memoize
         (log/info :bootstrap.asami/names-cache))
    (log/info :bootstrap.server/service-map service-map)
    (http/start (http/create-server service-map))))

(defn in-home
  [path]
  (str (System/getProperty "user.home") path))

(defn start-dev []
  "Start a development environment using the config file at `conf-path`."
  (log/info :bootstrap/start {:dev? true})

  ;; NOTE: only bootstraps once in dev mode!
  ;; This facilitates quick server restarts when developing at the REPL.
  (if (not @loaded-conf)
    (-> (in-home "/.clarin-tei/repl-conf.edn")
        (load-conf!)
        (db/bootstrap!))
    (log/info :bootstrap.conf/skip true))

  (let [service-map (assoc (->service-map @loaded-conf) ::http/join? false)]
    (log/info :bootstrap.server/service-map service-map)
    (reset! server (http/start (http/create-server service-map)))))

(defn stop-dev []
  (log/info :bootstrap.server/stop true)
  (http/stop @server))

(defn restart-dev []
  (when @server
    (stop-dev))
  (start-dev))

(defn -main
  [& [conf-path]]
  (log/info :bootstrap/main conf-path)
  (cond
    (.exists (io/file conf-path))
    (do
      (log/info :bootstrap.conf/exists conf-path)
      (start-prod conf-path))

    conf-path
    (log/error :bootstrap.conf/unreadable conf-path)

    :else
    (log/error :bootstrap.conf/missing conf-path)))

(comment
  @loaded-conf
  (restart-dev)
  (start-dev)
  (stop-dev)
  #_.)
