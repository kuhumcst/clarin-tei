(ns dk.cst.pedestal-sp
  (:require [clojure.spec.alpha :as s]
            [io.pedestal.http.body-params :refer [body-params]]
            [saml20-clj.core :as saml]
            [saml20-clj.coerce :as saml-coerce]
            [ring-ttl-session.core :as ttl]
            [dk.cst.pedestal-sp.spec :as sp.spec]
            [dk.cst.pedestal-sp.saml :as sp.saml]
            [dk.cst.pedestal-sp.auth :as sp.auth]))

(def ^:private default-paths
  {:saml-meta       "/saml/meta"
   :saml-login      "/saml/login"
   :saml-logout     "/saml/logout"
   :saml-session    "/saml/session"
   :saml-request    "/saml/session/request"
   :saml-response   "/saml/session/response"
   :saml-assertions "/saml/session/assertions"})

(defn expand-conf
  "Expand a `base-conf` with default and derived parameters. This allows for a
  minimal amount of configuration and provides internal consistency."
  [{:keys [app-name
           sp-url
           idp-url
           idp-cert
           relay-state                                      ; only fallback
           credential
           state-manager
           validation
           paths
           session
           no-auth]
    :as   base-conf}]
  {:pre  [(s/valid? ::sp.spec/config base-conf)]
   :post [(s/valid? ::sp.spec/config %)]}
  (let [{:keys [saml-login saml-meta] :as paths*} (merge default-paths paths)
        {:keys [cookie-attrs]} session
        max-age*       (or (:max-age cookie-attrs) (* 60 60 8))
        acs-url        (str sp-url saml-login)
        state-manager* (or state-manager (saml/in-memory-state-manager 60))
        validation*    (merge {:acs-url       acs-url
                               :state-manager state-manager}
                              validation)
        cookie-attrs*  (merge {:http-only true
                               #_#_:secure true             ;TODO: re-enable to enforce https
                               :max-age   max-age*}
                              cookie-attrs)
        session*       (merge {:cookie-name  "pedestal-sp"
                               :store        (ttl/ttl-memory-store max-age*)
                               :cookie-attrs cookie-attrs*}
                              (dissoc session :cookie-attrs))]
    (assoc base-conf

      ;; Derived
      :sp-name app-name                                     ; TODO: same or not?
      :sp-cert (saml/->X509Certificate credential)
      :sp-private-key (saml-coerce/->PrivateKey credential)
      :acs-url acs-url
      :issuer (str sp-url saml-meta)

      ;; Defaults
      :state-manager state-manager*
      :validation validation*
      :paths paths*
      :session session*)))

(defn saml-routes
  "Create SAML routes in table syntax based on a `conf` map."
  [{:keys [paths] :as conf}]
  (let [{:keys [saml-meta
                saml-login
                saml-logout
                saml-session
                saml-request
                saml-response
                saml-assertions]} paths
        body-params    (body-params)
        all            (sp.auth/permit conf :all)
        authenticated  (sp.auth/permit conf :authenticated)
        auth-requested (sp.auth/permit conf #(get-in % [:session :saml :request]))]
    ;; Standard endpoints required for an sp-initiated SAML login flow
    #{[saml-meta :get (sp.saml/metadata conf) :route-name ::saml-meta]
      [saml-login :get (conj all (sp.saml/request conf)) :route-name ::saml-req]
      [saml-login :post (conj all body-params (sp.saml/response conf)) :route-name ::saml-resp]

      ;; Logout endpoint, similar to - but not part of - the standard endpoints
      [saml-logout :post (conj all body-params `sp.saml/logout)]

      ;; User-centric metadata endpoints, not related to the SAML login flow
      [saml-session :get (conj all `sp.saml/echo-session)]
      [saml-request :get (conj auth-requested `sp.saml/echo-request)]
      [saml-response :get (conj authenticated `sp.saml/echo-response)]
      [saml-assertions :get (conj authenticated `sp.saml/echo-assertions)]}))
