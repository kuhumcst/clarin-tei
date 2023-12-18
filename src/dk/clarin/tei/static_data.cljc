(ns dk.clarin.tei.static-data
  (:require [clojure.set :as set]))

(def real-entity-types
  "The core searchable entities (with index pages)."
  {:entity.type/person
   {:img-src "/images/person-sharp-svgrepo-com.svg"}

   :entity.type/century
   {:img-src "/images/hourglass-2.svg"}})

;:entity.type/language
;{:img-src "/images/speech-bubble-svgrepo-com.svg"}})

;:entity.type/domain
;{:img-src "/images/university-svgrepo-com.svg"}
;
;:entity.type/archive
;{:img-src "/images/archive-svgrepo-com.svg"}
;
;:entity.type/publication
;{:img-src "/images/book-fill.svg"}
;
;:entity.type/term
;{:img-src "/images/label-svgrepo-com.svg"}
;
;:entity.type/english-term
;{:img-src "/images/label-svgrepo-com.svg"}
;
;:entity.type/place
;{:img-src "/images/earth-fill.svg"}
;
;:entity.type/organisation
;{:img-src "/images/people-group-svgrepo-com.svg"}
;
;:entity.type/linguistic-organisation
;{:img-src "/images/people-group-svgrepo-com.svg"}})

(def special-entity-types
  "These do not correspond to actual entities, but rather to searchable
  attributes that we want to be able to filter by in searches.

  The keys of the :en->da map correspond to the set of allowed values."
  {:entity.type/unknown
   {:img-src "/images/question-mark-in-circular-shape-svgrepo-com.svg"}

   :document/condition
   ;; TODO: move translations to i18n ns??
   {:en->da  {"transcribed" "transkriberet"}
    :img-src "/images/paper-sheet-svgrepo-com.svg"}})

(def static-entities
  [{:db/ident         "#unknown_person"
    :entity/type      :entity.type/person
    :entity/full-name "???"}])

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
  {:document/author    {:compatible #{:entity.type/person}}
   :document/century   {:compatible #{:entity.type/century}}

   ;; Special relations -- various strings treated as searchable entities.
   :document/condition {:compatible #{:document/condition}}})

(def order-rels
  {:document/year {:type nil}                               ; not supported
   :document/date {:type "date"}})

;; Used for select-keys (NOTE: relies on n<8 keys to keep order)
(def search-result-rels
  [:document/date
   :document/author])

(def reader-rels
  [:document/date
   :document/author])
