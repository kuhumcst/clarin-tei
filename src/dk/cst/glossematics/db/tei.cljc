(ns dk.cst.glossematics.db.tei
  "Scrape metadata from TEI documents in both the frontend and the backend."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [clojure.set :as set]
            #?(:clj  [io.pedestal.log :as log]
               :cljs [lambdaisland.glogi :as log])
            [dk.cst.cuphic :as cup]
            [dk.cst.cuphic.xml :as xml]
            [dk.cst.glossematics.shared :as shared]
            [dk.cst.glossematics.static-data :as sd])
  #?(:clj (:import [java.time.temporal ChronoField]
                   [java.time.format DateTimeFormatterBuilder])))

;; For some reason, the Clarin TEI files contain extra (invalid) declarations
;; which completely break parsing in the browser.
(defn- remove-oxygen-declaration
  [xml]
  (str/replace xml #"<\?oxygen .+\?>\s?" ""))

(def utc-dtf'
  "NOTE: Defaults to 1 january in case either is missing."
  #?(:clj  (-> (DateTimeFormatterBuilder.)
               (.appendPattern "yyyy[-MM[-dd]]")
               (.parseDefaulting ChronoField/MONTH_OF_YEAR 1)
               (.parseDefaulting ChronoField/DAY_OF_MONTH 1)
               (.toFormatter))
     :cljs shared/utc-dtf))

;; TODO: is ?optional switched with non-optional? see :document-type
;; TODO: the ... pattern not working correctly in Cuphic?
(def header-patterns
  {:language      '[:language {:ident language} ???]
   :title         '[:titleStmt {} [:title {} title] ???]
   :notes         '[:notesStmt {} [:note notes]]
   :date          '[:creation [:date {:when date}]]

   :author        '[:author [:name {:ref author} author-name]]
   :place         '[:msIdentifier {}
                    [:placeName {:ref place} _]
                    ???]
   :settlement    '[:settlement {:ref settlement} ???]
   :repository    '[:repository {:ref repository} title]
   :collection    '[:collection {} collection]
   :object-desc   '[:objectDesc {:form form}
                    [:supportDesc {}
                     [:support {} support]
                     [:extent {}
                      [:note {} page-count]]]]
   :hand-desc     '[:handDesc {} [:p {} hand]]
   :ms-desc       '[:msDesc {:ana publish-state} ???]
   :relevant-for  '[:ref {:type   "relevant_for"
                          :target target}]

   ;; Explodes [:correspDesc ...] into its constituent parts.
   :sender        '[:correspAction {:type "sent"}
                    ??? [:persName {:ref sender} ???] ???]
   :sender-loc    '[:correspAction {:type "sent"}
                    ??? [:placeName {:ref sender-loc}] ???]
   :sent-at       '[:correspAction {:type "sent"}
                    ??? [:date {} sent-at] ???]
   :recipient     '[:correspAction {:type "received"}
                    ??? [:persName {:ref recipient} ???] ???]
   :recipient-loc '[:correspAction {:type "received"}
                    ??? [:placeName {:ref recipient-loc}] ???]})

(def text-patterns
  {:facsimile '[:pb {:facs facs}]})

(defn scrape-document
  [xml]
  (let [hiccup (xml/parse xml)
        header (nth hiccup 2)
        text   (nth hiccup 3)]
    (merge
      (cup/scrape header header-patterns)
      (cup/scrape text text-patterns))))

(def placeholder?
  #{"n/a" "#n/a" "0"})

(defn valid?
  [s]
  (not (or (str/blank? s)
           (placeholder? s))))

(defn valid-int?
  [s]
  (and (valid? s)
       (re-matches #"\d+" s)))

(defn valid-id?
  [s]
  (and (valid? s)
       ;; TODO: make Dorte streamline archive IDs in the TEI files
       (or (str/starts-with? s "n")                         ; used for archives
           (str/starts-with? s "#n"))
       (or (re-matches #"#ns..." s)                         ; refer to sprog.txt
           (re-find #"\d$" s))))

(defn valid-date?
  [s]
  (re-matches #"\d\d\d\d-\d\d-\d\d" s))

;; Since Dorte's IDs sometimes have a prefixed # and sometimes don't
(defn fix-id
  [id]
  (if (str/starts-with? id "#")
    id
    (str "#" id)))

;; Since Heidi has been manually modifying some facsimile IDs to this standard.
;; e.g. acc-1992_0005_030_Western_0110-tei-final.xml
(defn fix-facsimile-id
  [facsimile]
  (cond
    (str/starts-with? facsimile "facc-")
    (subs facsimile 1)

    (str/starts-with? facsimile "#facc-")
    (str "#acc-" (subs facsimile 6))

    :else facsimile))

(def language-ref
  {"en"  "#nseng"
   "eng" "#nseng"                                           ; used in some TEI documents
   "fr"  "#nsfre"
   "da"  "#nsdan"
   "de"  "#nsger"})

(defn single-val
  [result k]
  (-> (get result k) first (get (symbol k))))

(defn single-triple
  [result filename validation-fn rel k]
  (when-let [v (single-val result k)]
    (when (validation-fn v)
      [filename rel v])))

(def parse-int
  #?(:clj  parse-long
     :cljs js/parseInt))

(defn- support-triple
  [filename v]
  (if (get (-> sd/special-entity-types :document/condition :en->da) v)
    [filename :document/condition v]
    (log/info :tei/unsupported {:document/condition v})))

(defn document-triples
  [filename {:keys [facsimile] :as result}]
  (let [triple (partial single-triple result filename)]
    (disj
      (reduce
        into
        (reduce
          conj
          #{}
          [(triple valid? :document/title :title)
           (triple valid? :document/language :language)
           (triple valid? :document/notes :notes)
           (triple valid? :document/date :date)
           (triple valid? :document/author :author)])
        [(for [{:syms [facs]} facsimile]
           [filename :document/facsimile facs])])
      nil)))

(defn ->triples
  "Create Asami triples from either a `filepath` or `filename`/`content` combo."
  #?(:clj ([filepath]
           (let [file    (io/file filepath)
                 content (-> filepath slurp remove-oxygen-declaration)]
             (document-triples (.getName file) (scrape-document content)))))
  ([filename content]
   (document-triples filename (scrape-document content))))

(defn triples->entity
  "Assemble Asami `triples` into an Asami entity."
  [triples]
  (let [ident (ffirst triples)]
    (->> (for [[_ k v] triples]
           {k #{v}})
         (apply merge-with set/union {:db/ident ident}))))

(def file->entity
  (comp triples->entity ->triples))

(comment
  (def example (io/file "/Users/rqf595/everyman-corpus/druk_1666/druk_1666_CTB.xml"))
  (xml/parse (remove-oxygen-declaration (slurp example)))
  (scrape-document (slurp "/Users/rqf595/everyman-corpus/druk_1666/druk_1666_CTB.xml"))
  (->triples "/Users/rqf595/everyman-corpus/druk_1666/druk_1666_CTB.xml")
  (triples->entity (->triples "example.xml" (slurp example)))
  #_.)