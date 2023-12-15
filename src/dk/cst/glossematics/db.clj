(ns dk.cst.glossematics.db
  "Functions for populating & querying the Glossematics Asami database."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [asami.core :as d]
            [io.pedestal.log :as log]
            [dk.cst.glossematics.static-data :as sd]
            [dk.cst.glossematics.shared :as shared]
            [dk.cst.glossematics.backend.shared :as bshared]
            [dk.cst.glossematics.db.file :as db.file]
            [dk.cst.glossematics.db.paper :as db.paper]
            [dk.cst.glossematics.db.person :as db.person]
            [dk.cst.glossematics.db.timeline :as db.timeline]
            [dk.cst.glossematics.db.tei :as db.tei]))

;; Syntax errors (fixed)
;; acc-1992_0005_025_Jakobson_0180-tei-final.xml:127:64

(defonce conn
  (d/connect "asami:mem://glossematics"))

(defn- puri
  [db-dir]
  (str "asami:local://" db-dir))

(defn pconn
  "Get a connection to the persisted storage graph located in `db-dir`.

  While the regular in-memory graph in 'conn' is a product of the input dataset,
  the persisted storage graph returned by 'pconn' is a smaller one consisting
  only of user-submitted data, e.g. bookmarks or comments."
  [db-dir]
  (d/connect (puri db-dir)))

(defn- multiple?
  [x]
  (and (coll? x) (> (count x) 1)))

(defn- as-set
  [v]
  (cond
    (set? v) v
    (some? v) #{v}))

(defn other-entities
  "Load TSV resource from `file` with the given `id-prefix` and `entity-type`;
  the returned data can be transacted into Asami."
  [file id-prefix entity-type]
  (->> (-> file bshared/resource io/input-stream io/reader line-seq dedupe)
       (map #(str/split % #"\t"))
       (map (fn [[id full-name]]
              {:db/ident         (str id-prefix id)
               :entity/type      (keyword "entity.type" entity-type)
               :entity/full-name (shared/capitalize-all full-name)}))))

(defn tei-files
  [conn]
  (d/q '[:find [?path ...]
         :where
         [?e :file/extension "xml"]
         [?e :file/path ?path]]
       conn))

(defn bookmarks
  [conn assertions author]
  (d/q (cond-> '[:find [?ident ...]
                 :in $ ?author
                 :where
                 [?e :entity/type :entity.type/bookmark]
                 [?e :bookmark/author ?author]
                 [?e :db/ident ?ident]]

         ;; Authorization required for non-public bookmarks!
         (not= author (shared/assertions->user-id assertions))
         (conj '[?e :bookmark/visibility :public]))

       conn author))

(defn entity-triples
  "Find the triples in `conn` of the entity identified by `ident` (:db/ident)."
  [conn ident]
  (d/q '[:find ?e ?a ?v
         :in $ ?ident
         :where
         [?e :db/ident ?ident]
         [?e ?a ?v]]
       conn ident))

(defn- retracted-eav
  [[e a v]]
  [:db/retract e a v])

(defn retract-entity!
  "Retracts the entity in `conn` identified by `ident`."
  [conn ident]
  (when-let [triples (entity-triples conn ident)]
    (d/transact conn {:tx-data (map retracted-eav triples)})
    (log/info :asami/retract-entity {:db/ident ident})))

(defn- log-transaction!
  "Transact `tx-data`, logging its count using the supplied `description`."
  [description tx-data]
  (log/info (keyword "bootstrap.asami" (str (name description) "-tx"))
            (count tx-data))
  (d/transact conn {:tx-data tx-data}))

(defn bootstrap!
  "Asynchronously bootstrap an in-memory Asami database from a `conf`."
  [{:keys [files-dir db-dir] :as conf}]
  ;; Ensure that persisted storage exists and can be accessed.
  (log/info :bootstrap.asami/persisted-storage {:db (pconn db-dir)})

  ;; TODO: remove these
  (comment
    (log-transaction! :timeline (db.timeline/timeline-entities))

    ;; Search entities
    (log-transaction! :repositories sd/repositories)
    (log-transaction! :person (db.person/person-entities))
    (log-transaction! :linguistic-organisation (other-entities "Lingvistiske_organisationer_og_konferencer-gennemgået-FINAL.txt" "#nlingorg" "linguistic-organisation"))
    (log-transaction! :organisation (other-entities "Organisationer-gennemgået-FINAL.txt" "#norg" "organisation"))
    (log-transaction! :publication (other-entities "Publikationer-gennemgået-FINAL.txt" "#npub" "publication"))
    (log-transaction! :language (other-entities "sprog.txt" "#ns" "language"))
    (log-transaction! :place (other-entities "Stednavne-gennemgået-FINAL.txt" "#npl" "place"))
    (log-transaction! :terms (other-entities "terms.txt" "#nt" "term"))
    (log-transaction! :english-terms (other-entities "terms-eng.txt" "#nteng" "english-term"))
    (log-transaction! :domain (other-entities "Domain.txt" "#ndom" "domain"))

    ;; Add the file entities found in the files-dir.
    ;; Then parse each TEI file and link the document data to the file entities.
    (log-transaction! :paper db.paper/static-data))

  (log-transaction! :files (db.file/file-entities files-dir))
  (let [file-entities  (map db.tei/file->entity (tei-files conn))
        named-entities (mapcat (comp :entities meta) file-entities)]
    (log-transaction! :tei-data file-entities)
    (log-transaction! :named-entities named-entities)))

(comment
  (bootstrap! {:files-dir "/Users/rqf595/everyman-corpus"
               :db-dir    "/Users/rqf595/.clarin-tei/db"})
  (count (tei-files conn))

  ;; Delete everything in persisted storage -- for development use.
  (d/delete-database (puri "/Users/rqf595/.clarin-tei/db"))


  (db.file/file-entities "/Users/rqf595/everyman-corpus")
  ;; Test persisted storage
  (d/transact (pconn "/Users/rqf595/.clarin-tei/db")
              {:tx-data [{:db/ident   "glen"
                          :glen/name  "Glen"
                          :glen/thing 123}]})
  (d/entity (pconn "/Users/rqf595/.clarin-tei/db") "glen")
  (d/q '[:find ?path .
         :in $ ?name
         :where
         [?e :file/name ?name]
         [?e :file/path ?path]]
       conn "druk_1666_CTB.xml")

  (bookmarks (pconn "/Users/rqf595/.clarin-tei/db")
             {}
             "UNKNOWN")

  ;; Multiple names registered for the same person (very common)
  (d/entity conn "#np668")

  ;; Test loading of file entities
  (d/entity conn "acc-1992_0005_036_Uldall_0220-tei-final.xml")
  (d/entity conn "acc-1992_0005_134_Sprogteor_0130-tei.xml")
  (d/entity conn "acc-1992_0005_124_Cenematics_0100_098.tif.jpg")

  (count (d/q '[:find ?name ?path
                :where
                [?e :file/extension "jpg"]
                [?e :file/name ?name]
                [?e :file/path ?path]]
              conn))
  #_.)
