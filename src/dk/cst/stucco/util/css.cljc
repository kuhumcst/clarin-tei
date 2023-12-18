(ns dk.cst.stucco.util.css
  "CSS content available programmatically for use in the Shadow DOM."
  (:require [shadow.resource :as resource]
            [clojure.string :as str]))

(def resources
  {:document (resource/inline "dk/clarin/tei/public/css/document.css")
   :group    (resource/inline "dk/clarin/tei/public/css/group.css")
   :pattern  (resource/inline "dk/clarin/tei/public/css/pattern.css")
   :shared   (resource/inline "dk/clarin/tei/public/css/shared.css")})

(def default-theme
  (resource/inline "dk/clarin/tei/public/css/theme.css"))

(def shadow-style
  "The combined CSS content - including the default theme - for all widgets."
  (let [titles (->> (keys resources)
                    (map #(str "\n\n/*\n\t === " (name %) ".css ===\n*/\n")))
        theme  (str/replace-first default-theme ":root" ":host")]
    (->> (vals resources)
         (interleave titles)
         (apply str "/*\n\t === theme.css ===\n*/\n" theme))))
