(ns dk.clarin.tei.db
  "Functions for populating & querying the CLARIN TEI Asami database."
  (:require [asami.core :as d]
            [dk.clarin.tei.static-data :as sd]
            [io.pedestal.log :as log]
            [dk.clarin.tei.shared :as shared]
            [dk.clarin.tei.db.file :as db.file]
            [dk.clarin.tei.db.tei :as db.tei]))

(defonce conn
  (d/connect "asami:mem://clarin-tei"))

(defn- puri
  [db-dir]
  (str "asami:local://" db-dir))

(defn pconn
  "Get a connection to the persisted storage graph located in `db-dir`.

  While the regular in-memory graph in 'conn' is a product of the input dataset,
  the persisted storage graph returned by 'pconn' is a smaller one consisting
  only of user-submitted data."
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

(defn tei-files
  [conn]
  (d/q '[:find [?path ...]
         :where
         [?e :file/extension "xml"]
         [?e :file/path ?path]]
       conn))

(defn dk5-ids
  [conn]
  (set (d/q '[:find [?id ...]
              :where
              [?e :document/dk5 ?id]]
            conn)))

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
  #_(log/info :bootstrap.asami/persisted-storage {:db (pconn db-dir)})
  (log-transaction! :files (db.file/file-entities files-dir))
  (let [file-entities  (map db.tei/file->entity (tei-files conn))
        named-entities (concat (mapcat (comp :entities meta) file-entities)
                               sd/static-entities)]
    (log-transaction! :tei-data file-entities)
    (log-transaction! :named-entities named-entities)))

(comment
  (bootstrap! {:files-dir "/Users/rqf595/everyman-corpus"
               :db-dir    "/Users/rqf595/.clarin-tei/db"})
  (count (tei-files conn))
  (dk5-ids conn)

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
