(ns dk.cst.glossematics.static-data
  (:require [clojure.set :as set]))

(def real-entity-types
  "The core searchable entities (with index pages)."
  {:entity.type/domain
   {:img-src "/images/university-svgrepo-com.svg"}

   :entity.type/archive
   {:img-src "/images/archive-svgrepo-com.svg"}

   :entity.type/person
   {:img-src "/images/person-sharp-svgrepo-com.svg"}

   :entity.type/publication
   {:img-src "/images/book-fill.svg"}

   :entity.type/term
   {:img-src "/images/label-svgrepo-com.svg"}

   :entity.type/english-term
   {:img-src "/images/label-svgrepo-com.svg"}

   :entity.type/language
   {:img-src "/images/speech-bubble-svgrepo-com.svg"}

   :entity.type/place
   {:img-src "/images/earth-fill.svg"}

   :entity.type/organisation
   {:img-src "/images/people-group-svgrepo-com.svg"}

   :entity.type/linguistic-organisation
   {:img-src "/images/people-group-svgrepo-com.svg"}})

(def special-entity-types
  "These do not correspond to actual entities, but rather to searchable
  attributes that we want to be able to filter by in searches.

  The keys of the :en->da map correspond to the set of allowed values."
  {:entity.type/unknown
   {:img-src "/images/question-mark-in-circular-shape-svgrepo-com.svg"}

   :document/condition
   ;; TODO: move translations to i18n ns??
   {:en->da  {"transcribed"  "transkriberet"

              "original"     "original"
              "photocopy"    "fotokopi"
              "carbon copy"  "gennemslagspapir"

              ;; publish state
              "published"    "publiceret"
              "unpublished"  "upubliceret"
              "draft"        "udkast"

              ;; form
              "postcard"     "postkort"
              "document"     "dokument"
              "letter"       "brev"

              ;; hand
              "stenographed" "stenografi"
              "typed"        "maskinskrevet"
              "handwritten"  "håndskrevet"}
    :img-src "/images/paper-sheet-svgrepo-com.svg"}})

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
   :document/language  {:compatible #{:entity.type/language}}

   ;; Special relations -- various strings treated as searchable entities.
   :document/condition {:compatible #{:document/condition}}})

(def order-rels
  {:document/date {:type "date"}})

;; Used for select-keys (NOTE: relies on n<8 keys to keep order)
(def search-result-rels
  [:document/date
   :document/author
   :document/condition])

(def reader-rels
  [:document/date
   :document/author
   :document/condition])

(def author->id
  {"lh"  "#np56"
   "efj" "#np40"
   "pd"  "#np33"})

(def id->author
  (set/map-invert author->id))
