(ns dk.clarin.tei.static-data
  (:require [clojure.set :as set]))

(def real-entity-types
  "The core searchable entities (with index pages)."
  {:entity.type/person
   {:img-src "/tei/images/person-sharp-svgrepo-com.svg"}

   :entity.type/century
   {:img-src "/tei/images/hourglass-2.svg"}

   :entity.type/category
   {:img-src "/tei/images/book-fill.svg"}})

;:entity.type/language
;{:img-src "/tei/images/speech-bubble-svgrepo-com.svg"}})

;:entity.type/domain
;{:img-src "/tei/images/university-svgrepo-com.svg"}
;
;:entity.type/archive
;{:img-src "/tei/images/archive-svgrepo-com.svg"}
;
;:entity.type/publication
;{:img-src "/tei/images/book-fill.svg"}
;
;:entity.type/term
;{:img-src "/tei/images/label-svgrepo-com.svg"}
;
;:entity.type/english-term
;{:img-src "/tei/images/label-svgrepo-com.svg"}
;
;:entity.type/place
;{:img-src "/tei/images/earth-fill.svg"}
;
;:entity.type/organisation
;{:img-src "/tei/images/people-group-svgrepo-com.svg"}
;
;:entity.type/linguistic-organisation
;{:img-src "/tei/images/people-group-svgrepo-com.svg"}})

(def special-entity-types
  "These do not correspond to actual entities, but rather to searchable
  attributes that we want to be able to filter by in searches.

  The keys of the :en->da map correspond to the set of allowed values."
  {:document/condition
   ;; TODO: move translations to i18n ns??
   {:en->da  {"transcribed" "transkriberet"}
    :img-src "/tei/images/paper-sheet-svgrepo-com.svg"}})

(def static-entities
  [{:db/ident         "#unknown_person"
    :entity/type      :entity.type/person
    :entity/full-name "???"}

   ;; DK5 categories in use
   {:db/ident         "#dk550",
    :entity/type      :entity.type/category,
    :entity/full-name "Naturvidenskab i alm. (50)"}
   {:db/ident         "#dk551",
    :entity/type      :entity.type/category,
    :entity/full-name "Matematik (51)"}
   {:db/ident         "#dk540",
    :entity/type      :entity.type/category,
    :entity/full-name "Geografi og rejser i alm. (40)"}
   {:db/ident         "#dk5641",
    :entity/type      :entity.type/category,
    :entity/full-name "Madlavning (64.1)"}
   {:db/ident         "#dk561",
    :entity/type      :entity.type/category,
    :entity/full-name "Medicin. Sundhedsvidenskab (61)"}
   {:db/ident         "#dk5195",
    :entity/type      :entity.type/category,
    :entity/full-name "Kommunikation i alm. Information i alm. (19.5)"}
   {:db/ident         "#dk5371",
    :entity/type      :entity.type/category,
    :entity/full-name "Undervisning i alm. (37.1)"}
   {:db/ident         "#dk590",
    :entity/type      :entity.type/category,
    :entity/full-name "Historie i alm. (90)"}
   {:db/ident         "#dk520",
    :entity/type      :entity.type/category,
    :entity/full-name "Kristendom i alm. (20)"}
   {:db/ident         "#dk510",
    :entity/type      :entity.type/category,
    :entity/full-name "Filosofi i alm. (10)"}])

(def en-attr->da-attr
  (apply merge (map (comp :en->da second) special-entity-types)))

(def en-attr->en-attr
  (zipmap (keys en-attr->da-attr) (keys en-attr->da-attr)))

(def da-attr->en-attr
  (set/map-invert en-attr->da-attr))

(def entity-types
  (merge real-entity-types special-entity-types))

(def danish-letter->ascii
  {"æ" "ae"
   "Æ" "Ae"
   "å" "aa"
   "Å" "Aa"
   "ø" "oe"
   "Ø" "Oe"})

;; TODO: add year/date?
(def search-rels
  {:document/author     {:compatible #{:entity.type/person}}
   :document/translator {:compatible #{:entity.type/person}}
   :document/editor     {:compatible #{:entity.type/person}}
   :document/publisher  {:compatible #{:entity.type/person}}
   :document/century    {:compatible #{:entity.type/century}}
   :document/dk5        {:compatible #{:entity.type/category}}

   ;; Special relations -- various strings treated as searchable entities.
   :document/condition  {:compatible #{:document/condition}}})

(def order-rels
  {:document/year {:type nil}                               ; not supported
   :document/date {:type "date"}})

;; Used for select-keys (NOTE: relies on n<8 keys to keep order)
(def search-result-rels
  [:document/author
   :document/date
   :document/century
   :document/dk5])

(def reader-rels
  [:document/date
   :document/author])
