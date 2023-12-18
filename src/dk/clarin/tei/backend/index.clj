(ns dk.clarin.tei.backend.index
  "Generate the index.html file using Clojure. This is mostly done to streamline
  fingerprinting of any included files in the release version."
  (:require [hiccup.core :as hiccup]
            [dk.cst.pedestal.sp.auth :as sp.auth]
            [dk.clarin.tei.backend.shared :as bshared])
  (:import [java.util Date]))

(def init-hash
  (hash (Date.)))

;; https://javascript.plainenglish.io/what-is-cache-busting-55366b3ac022
(defn- cb
  "Decorate the supplied `path` with a cache busting string."
  [path]
  (str path "?" init-hash))

(defn index-hiccup
  [assertions saml-paths negotiated-language]
  [:html {:lang "da"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name    "viewport"
            :content "width=device-width, initial-scale=1.0"}]
    [:title (str (when bshared/development? "(dev) ") "Clarin TEI")]
    [:link {:rel "icon" :href (cb "/images/favicon.svg")}]
    [:link {:rel "mask-icon" :href (cb "/images/favicon.svg") :color "#a02c2c"}]
    [:link {:rel "stylesheet" :href (cb "/css/main.css")}]
    [:link {:rel "stylesheet" :href (cb "/css/theme.css")}]]
   [:body
    [:div#app]
    [:script
     ;; Rather than having an extra endpoint that the SPA needs to access, these
     ;; values are passed on to the SPA along with the compiled main.js code.
     (str "var SAMLAssertions = '" (pr-str assertions) "';\n"
          "var SAMLPaths = '" (pr-str saml-paths) "';\n"
          "var negotiatedLanguage = '" (pr-str negotiated-language) "';\n"
          "var inDevelopmentEnvironment = " bshared/development? ";\n")]
    [:script {:src (cb (str "/js/compiled/" bshared/main-js))}]]])

(defn index-html
  [assertions saml-paths negotiated-language]
  (str
    "<!DOCTYPE html>"
    (hiccup/html (index-hiccup assertions saml-paths negotiated-language))))

(defn handler
  [{:keys [accept-language] :as request}]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (index-html (sp.auth/request->assertions request)
                        (-> request :conf :paths)
                        accept-language)})

(defn shadow-handler
  "Handler used by the shadow-cljs watch process which overrides auth."
  [request]
  (handler (assoc-in request [:session :saml :assertions :condition] :all)))
