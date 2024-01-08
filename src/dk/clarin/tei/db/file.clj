(ns dk.clarin.tei.db.file
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.io File]))

;; TODO: are any of them not transcribed?
(defn- with-body?
  "Does the file with `filename` contain a body of content?"
  [filename]
  true
  #_(str/ends-with? filename "-final.xml"))

(def file-entities-xf
  (comp
    (remove #(.isDirectory ^File %))
    (map (fn [file]
           (let [filename  (.getName ^File file)
                 extension (last (str/split filename #"\."))
                 path      (.getPath ^File file)
                 m         {:db/ident       filename
                            :entity/type    :entity.type/file
                            :file/name      filename
                            :file/extension extension
                            :file/path      path}]
             (if (with-body? filename)
               (assoc m :document/condition "transcribed")
               m))))))

(def duplicates-xf
  (comp
    (map :file/name)
    (filter with-body?)
    (map (fn [s]
           (str (subs s 0 (dec (count s))) ".xml")))))

(defn file-entities
  "Recursively list all file entities found in `dir`, ignoring directories.
  Duplicates of the transcribed TEI files with empty bodies are not included."
  [dir]
  (let [entities   (into [] file-entities-xf (file-seq (io/file dir)))
        duplicates (into #{} duplicates-xf entities)]
    (remove (comp duplicates :file/name) entities)))

(comment
  (file-entities "/Users/rqf595/everyman-corpus")
  (filter (comp #{"xml"} :file/extension) (file-entities "/Users/rqf595/everyman-corpus"))
  (count (file-entities "/Users/rqf595/everyman-corpus"))
  #_.)
