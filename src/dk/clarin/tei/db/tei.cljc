(ns dk.clarin.tei.db.tei
  "Scrape metadata from TEI documents in both the frontend and the backend."
  (:require [clojure.string :as str]
            #?(:clj [clojure.java.io :as io])
            [clojure.set :as set]
            #?(:clj  [io.pedestal.log :as log]
               :cljs [lambdaisland.glogi :as log])
            [dk.cst.cuphic :as cup]
            [dk.cst.cuphic.xml :as xml]))

;; For some reason, the Clarin TEI files contain extra (invalid) declarations
;; which completely break parsing in the browser.
(defn- remove-oxygen-declaration
  [xml]
  (str/replace xml #"<\?oxygen .+\?>\s?" ""))

(def header-patterns
  {:language    '[:language {:ident language} ???]
   :title       '[:titleStmt {} [:title {} title] ???]
   :notes       '[:notesStmt {} [:note notes]]
   :date        '[:creation [:date {:when date}]]
   :dk5         '[:domain {:type "general"} dk5]
   :urn         '[:idno {:type "urn"} urn]
   :translators '[:respStmt {:n "translators"}
                  [:resp "Translated by"]
                  [:name {:ref translator} translator-name]]
   :publisher   '[:publisher {:n publisher} publisher-name]
   :editor      '[:editor
                  [:name {:ref editor} editor-name]]
   :author      '[:author [:name {:ref author} author-name]]})

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

(defn single-val
  [result k]
  (-> (get result k) first (get (symbol k))))

(defn single-triple
  [result filename validation-fn rel k]
  (when-let [v (single-val result k)]
    (when (validation-fn v)
      [filename rel v])))

(defn fix-full-name
  [s]
  (apply str (interpose " " (reverse (str/split s #", ")))))

(defn fix-id
  [id]
  (if (not (str/starts-with? id "#"))
    (str "#" id)
    id))

(def year-re
  #"^\d\d\d\d")

(defn century
  [date-str]
  (when date-str
    (some-> (re-find year-re date-str)
            (parse-long)
            (quot 100))))

(defn document-triples
  [filename {:keys [facsimile author editor publisher date dk5 translators] :as result}]
  (let [triple    (partial single-triple result filename)
        {:syms [author author-name]} (first author)
        {:syms [editor editor-name]} (first editor)
        {:syms [publisher publisher-name]} (first publisher)
        {:syms [translator translator-name]} (first translators)
        date'     (get (first date) 'date)
        cent      (century date')
        cent-id   (str "#c" cent)
        cent-name (str cent "00-tallet")
        dk5-index (when (not (empty? dk5))
                    (-> (first dk5)
                        (get 'dk5)
                        (->> (re-find #"^[\d-.]+")
                             (str "#dk5"))))]
    (with-meta
      (disj
        (reduce
          into
          (reduce
            conj
            #{}
            [(triple valid? :document/title :title)
             (triple valid? :document/language :language)
             (triple valid? :document/notes :notes)
             (triple valid? :document/urn :urn)
             (when dk5-index
               [filename :document/dk5 dk5-index])
             (when (valid? translator)
               [filename :document/translator (fix-id translator)])
             (when (valid? editor)
               [filename :document/editor (fix-id editor)])
             (when (valid? publisher)
               [filename :document/publisher (fix-id publisher)])

             (if cent
               [filename :document/century cent-id]
               [filename :document/century "#unknown_century"])
             (triple valid? :document/date :date)
             (if (valid? author)
               [filename :document/author (fix-id author)]
               [filename :document/author "#unknown_person"])])
          [(for [{:syms [facs]} facsimile]
             [filename :document/facsimile facs])
           (when (valid? date')
             [(when-let [year (re-find year-re date')]
                [filename :document/year (parse-long year)])
              (when-not (re-matches year-re date')
                [filename :document/date date'])])])
        nil)
      {:entities
       (remove nil? [(when (valid? author-name)
                       {:db/ident         (fix-id author)
                        :entity/type      :entity.type/person
                        :entity/full-name (fix-full-name author-name)})
                     (when (valid? translator)
                       {:db/ident         (fix-id translator)
                        :entity/type      :entity.type/person
                        :entity/full-name (fix-full-name translator-name)})
                     (when (valid? editor)
                       {:db/ident         (fix-id editor)
                        :entity/type      :entity.type/person
                        :entity/full-name (fix-full-name editor-name)})
                     (when (valid? publisher)
                       {:db/ident         (fix-id publisher)
                        :entity/type      :entity.type/person
                        :entity/full-name (fix-full-name publisher-name)})
                     (when cent
                       {:db/ident         cent-id
                        :entity/type      :entity.type/century
                        :entity/full-name cent-name})])})))

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
    (with-meta
      (->> (for [[_ k v] triples]
             {k #{v}})
           (apply merge-with set/union {:db/ident ident}))
      (meta triples))))


(defn thumbnail
  [entity]
  (str "thumb-" (first (sort (:document/facsimile entity))) ".jpg"))

(defn add-thumbnail
  [entity]
  (assoc entity :file/thumbnail (thumbnail entity)))

(def file->entity
  (comp add-thumbnail triples->entity ->triples))

(comment
  (century "1500")
  (century "2001")
  (century "2001-01-01")
  (fix-full-name "Horsens, Jens Albertsen")
  (def example (io/file "/Users/rqf595/everyman-corpus/tve_tid_1577/tve_tid_1577_CTB.xml"))
  (xml/parse (remove-oxygen-declaration (slurp example)))
  (scrape-document (slurp "/Users/rqf595/everyman-corpus/druk_1666/druk_1666_CTB.xml"))
  (->triples "/Users/rqf595/everyman-corpus/druk_1666/druk_1666_CTB.xml")
  (triples->entity (->triples "example.xml" (slurp example)))
  (-> (triples->entity (->triples "example.xml" (slurp example)))
      (add-thumbnail))

  (meta (triples->entity (->triples "example.xml" (slurp example))))
  #_.)