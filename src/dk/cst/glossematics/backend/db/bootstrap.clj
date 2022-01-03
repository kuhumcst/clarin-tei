(ns dk.cst.glossematics.backend.db.bootstrap
  (:require [dk.cst.cuphic :as cup]
            [dk.cst.cuphic.xml :as xml]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.io File]))

(def tei-files
  (let [dir (io/file "/Users/rqf595/Desktop/glossematics-files/tei")]
    (->> (file-seq dir)
         (remove #{dir}))))

(def rename
  {:placeName :place
   :persName  :person})

(defn ref-str
  [tag ?type]
  (if (= tag :rs)
    (name (get rename ?type ?type))
    (name (get rename tag tag))))

;; TODO: is ?optional switched with non-optional? see :document-type
;; TODO: the ... pattern not working correctly in Cuphic?
(def header-patterns
  {:language      '[:language {:ident language} ???]
   :title         '[:title {} title]
   :author        '[:author {:ref author} ???]
   :settlement    '[:settlement {:ref settlement} ???]
   :document-type '[:objectDesc {:form form}
                    [:supportDesc {}
                     [:support {} support]
                     [:extent {}
                      [:note {} page-count]]]]
   :header-refs   '[tag {:ref  ref
                         :type ?type} ???]                  ; TODO: not enough context
   :changes       '[:change {:when when :who who} ???]
   :header-dates  '[:date {:when when} ???]})

(def facsimile-patterns
  {:facsimile '[:graphic {:xml/id id}]})

(def text-patterns
  {:body-refs  '[tag {:ref  ref
                      :type ?type} ???]
   :body-dates '[:date {:when when} ???]})

(defn scrape-document
  [xml]
  (let [hiccup    (xml/parse xml)
        header    (nth hiccup 2)
        facsimile (nth hiccup 3)
        text      (nth hiccup 4)]
    (merge
      (cup/scrape header header-patterns)
      (cup/scrape facsimile facsimile-patterns)
      (cup/scrape text text-patterns))))

(def blank-ref?
  #{"xx" "#xx"})

(defn single-val
  [result k]
  (-> (get result k) first (get (symbol k))))

(defn single-triple
  [result filename rel k]
  (when-let [v (single-val result k)]
    (when true #_(not (blank-ref? v)) ;TODO don't include blanks
      [filename rel v])))

(defn document-triples
  [filename {:keys [header-refs
                    body-refs
                    facsimile
                    changes
                    body-dates]
             :as   result}]
  (let [triple (partial single-triple result filename)]
    (disj
      (reduce
        into
        (reduce
          conj
          #{}
          [(triple :document/title :title)
           (triple :document/author :author)
           (triple :document/editor :editor)
           (triple :document/language :language)
           (triple :document/settlement :settlement)])
        [(for [{:syms [when who]} changes]
           [filename :document/changedBy who])
         (for [{:syms [id]} facsimile]
           [filename :document/facsimile id])
         (for [{:syms [when]} body-dates]
           [filename :mention/date when])
         #_(for [{:syms [tag ref ?type]} header-refs]
             (when (str/starts-with? ref "#n")
               [filename (keyword "header" (ref-str tag ?type)) ref]))
         #_(for [{:syms [tag ref ?type]} body-refs]
             (when (str/starts-with? ref "#n")
               (let []
                 [filename (keyword "mention" (ref-str tag ?type)) ref])))])
      nil)))

(defn as-triples
  [^File file]
  (document-triples (.getName file) (scrape-document file)))

(comment
  (def example (nth tei-files 69))
  (xml/parse example)
  (nth (xml/parse example) 2)                               ; header
  (nth (xml/parse example) 3)                               ; facsimile
  (nth (xml/parse example) 4)                               ; text/body
  (scrape-document example)
  (document-triples "example.xml" (scrape-document example))
  (as-triples example)

  #_.)
