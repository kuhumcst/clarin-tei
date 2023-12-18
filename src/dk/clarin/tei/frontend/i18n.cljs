(ns dk.clarin.tei.frontend.i18n
  "English and Danish translations for the frontend pages."
  (:require [tongue.core :as tongue]
            [dk.clarin.tei.frontend.state :as state]
            [dk.clarin.tei.frontend :as-alias frontend]
            [dk.clarin.tei.frontend.shared :as-alias fshared]
            [dk.clarin.tei.frontend.page.main :as-alias main]
            [dk.clarin.tei.frontend.page.privacy :as-alias privacy]
            [dk.clarin.tei.frontend.page.bookmarks :as-alias bookmarks]
            [dk.clarin.tei.frontend.page.reader :as-alias reader]
            [dk.clarin.tei.frontend.page.search :as-alias search]))

;; Allow for positionally interpolated Hiccup vectors in translations.
;; NOTE: copy-pasted from https://github.com/tonsky/tongue#interpolation
(extend-type PersistentVector
  tongue/IInterpolate
  #_(interpolate-named [v dicts locale interpolations]
                       (mapv (fn [x]
                               (if (and (keyword? x)
                                        (= "arg" (namespace x)))
                                 (get interpolations x)
                                 x)) v))

  ;; TODO: modify? currently only supports arguments at top level.
  (interpolate-positional [v dicts locale interpolations]
    (mapv (fn [x]
            (if (and (vector? x)
                     (= :arg (first x)))
              (nth interpolations (second x))
              x)) v)))

(def frontend-translations
  {::frontend/main-caption         {:en "Go to the main page"
                                    :da "Gå til hovedsiden"}
   ::frontend/encyclopedia         {:en "Encyclopedia"
                                    :da "Encyklopædi"}
   ::frontend/local-reader         {:en "Local reader"
                                    :da "Lokal læser"}
   ::frontend/search               {:en "Search"
                                    :da "Søg"}
   ::frontend/search-caption       {:en "Find documents in our archive"
                                    :da "Find dokumenter i vores arkiv"}
   ::frontend/timeline             {:en "Timeline"
                                    :da "Tidslinje"}
   ::frontend/timeline-caption     {:en "Explore the life Louis Hjelmslev"
                                    :da "Udforsk Louis Hjelmslevs liv"}
   ::frontend/bibliography         {:en "Bibliography"
                                    :da "Bibliografi"}
   ::frontend/bibliography-caption {:en "View relevant works"
                                    :da "Se relevante værker"}
   ::frontend/language-flag        {:en [:img.language-icon {:src "/images/united-kingdom-svgrepo-com.svg"
                                                             :alt "Union Jack"}]
                                    :da [:img.language-icon {:src "/images/denmark-svgrepo-com.svg"
                                                             :alt "Dannebrog"}]}
   ::frontend/language-caption     {:en "English (klik for skifte til dansk)"
                                    :da "Dansk (click to switch to English)"}
   ::frontend/bookmarks            {:en "Bookmarks"
                                    :da "Bogmærker"}
   ::frontend/add-bookmark-caption {:en "Add bookmark for this page"
                                    :da "Opret bogmærke for denne side"}
   ::frontend/rem-bookmark-caption {:en "Remove bookmark for this page"
                                    :da "Fjern bogmærke for denne side"}
   ::frontend/a11y                 {:da "Tilgængelighed"
                                    :en "Accessibility"}
   ::frontend/privacy              {:da "Privatliv"
                                    :en "Privacy"}
   ::frontend/copyright            {:da [:<>
                                         [:span "© 2022 - "]
                                         [:a {:href "https://www.ku.dk/"}
                                          [:span.first "K"]
                                          "øbenhavns Universitet"]
                                         [:span " & "]
                                         [:a {:href "https://www.au.dk/"}
                                          [:span.first "A"]
                                          "arhus Universitet"]
                                         "."]
                                    :en [:<>
                                         [:span "© 2022 - "]
                                         [:a {:href "https://www.ku.dk/english/"}
                                          [:span.first "U"]
                                          "niversity of Copenhagen"]
                                         [:span " & "]
                                         [:a {:href "https://international.au.dk/"}
                                          [:span.first "A"]
                                          "arhus University"]
                                         "."]}
   ::frontend/unknown-page         {:en [:p "Unknown page."]
                                    :da [:p "Ukendt side."]}})

;; TODO: keep this around while salvaging content from here
(def main-page-translations
  {::main/logged-in-status   {:en [:p "You are currently " [:em "logged in"] " via your institution"
                                   " (" [:a {:href "/tei/bookmarks"} "bookmarks"] ")."]
                              :da [:p "Du er i øjeblikket " [:em "logget ind"] " via din institution"
                                   " (" [:a {:href "/tei/bookmarks"} "bogmærker"] ")."]}
   ::main/logged-in-status-1 {:en [:p "You are currently " [:em "logged in"] " via " [:arg 0]
                                   " (" [:a {:href "/tei/bookmarks"} "bookmarks"] ")."]
                              :da [:p "Du er i øjeblikket " [:em "logget ind"] " via " [:arg 0]
                                   " (" [:a {:href "/tei/bookmarks"} "bogmærker"] ")."]}
   ::main/log-out            {:en "Log out"
                              :da "Log ud"}
   ::main/log-out-long       {:en "Log out of Glossematics"
                              :da "Log ud af Glossematics"}
   ::main/user-details       {:en "User details"
                              :da "Brugerdetaljer"}
   ::main/logged-out-status  {:en [:p "You are currently " [:em "not"] " logged in. "]
                              :da [:p "Du er i øjeblikket " [:em "ikke"] " logget ind. "]}
   ::main/log-in             {:en "Log in"
                              :da "Log ind"}
   ::main/log-in-long        {:en "Log in to Glossematics using your institution"
                              :da "Log ind i Glossematics vha. din institution"}})

(def privacy-page-translations
  {::privacy/text {:da [:<>
                        [:p
                         "Clarin.dk indsamler ikke data om sine brugere til statistik eller andet.
                        Dog skal du forvente at dit besøg på siden logges, og at der lagres cookies
                        og andet data i det omfang du bruger siden."]
                        [:p
                         "Eksempelvis lagres dine sprogindstillinger lokalt i din browser,
                        hvis du ændrer sproget og din sessions-ID gemmes også som en cookie både i
                        din browser og på serveren, således at login-funktionaliteten fungerer.
                        Funktionalitet på siden som kræver at andet data gemmes, f.eks. bogmærker,
                        resulterer også i at data lagres på vores server."]
                        [:p
                         "Login håndteres helt transparent via din egen institutions login-side.
                        Når du logger ind, modtager vi en lille portion data, der identificerer dig.
                        De datapunkter vi modtager, kan du se under BRUGERDETALJER på forsiden."]]
                   :en [:<>
                        [:p
                         "Clarin.dk does not collect data about its users for statistics or other purposes.
                         However, you should expect that your visit to this page is logged and that cookies
                         and other data will be stored according to your site usage."]
                        [:p
                         "For example, your language settings are stored locally in your browser
                         if you change the language and your session ID is also stored as a cookie in both
                         your browser and on the server, such that logins are possible.
                         Any functionality on this site which requires saving other data, e.g. bookmarks,
                         also results in data being persisted on our server."]
                        [:p
                         "Login is handled completely transparently via the login page of your institution.
                         When you log in, we will receive a tiny bit of data identifying you.
                         You can inspect this data under USER DETAILS on the frontpage."]]}})

(def reader-page-translations
  {::reader/local-file        {:en "Local TEI file"
                               :da "Lokal TEI-fil"}
   ::reader/transcription     {:en "Transcription"
                               :da "Transkription"}
   ::reader/prev-results      {:en "previous results..."
                               :da "forrige resultater..."}
   ::reader/next-results      {:en "more results..."
                               :da "flere resultater..."}
   ::reader/placeholder       {:en ["N/A" "[content missing]"]
                               :da ["N/A" "[indhold mangler]"]}
   ::reader/illustration-of-1 {:en "Illustration of {1}"
                               :da "Illustration af {1}"}})

(def search-page-translations
  {::search/look-for     {:en "Look for"
                          :da "Led efter"}
   ::search/placeholder  {:en "e.g. place, person, organisation, …"
                          :da "f.eks. sted, person, organisation, …"}
   ::search/go           {:en "Search"
                          :da "Søg"}
   ::search/options      {:en "More options"
                          :da "Flere muligheder"}
   ::search/criteria     {:en "Criteria"
                          :da "Kriterier"}
   ::search/reset        {:en "Reset criteria"
                          :da "Nulstil kriterier"}
   ::search/remove       {:en "Remove criterion"
                          :da "Fjern kriterie"}
   ::search/add          {:en "Add another criterion"
                          :da "Tilføj endnu et kriterie"}
   ::search/prev         {:en "← previous"
                          :da "← forrige"}
   ::search/next         {:en "next →"
                          :da "næste →"}
   ::search/empty        {:en "No documents match the criteria. Perhaps try removing a criterion?"
                          :da "Ingen dokumenter matcher kriterierne.  Prøv evt. at fjerne et kriterie?"}
   ::search/empty-exact  {:en [:<> [:strong "NOTE:"] " search works better when you pick one of the " [:em "suggested entities"] " from the list."]
                          :da [:<> [:strong "BEMÆRK:"] " søgning fungerer bedre, når du vælger en af de " [:em "foreslåede entiteter"] " fra listen."]}
   ::search/view-caption {:en "View in the reader"
                          :da "Vis i læseren"}
   ::search/condition    {:en "Document condition"
                          :da "Dokumentets tilstand"}
   ::search/limit-from   {:en "Limit from "
                          :da "Begræns fra "}
   ::search/limit-to     {:en " to "
                          :da " til "}
   ::search/order-by     {:en "Sort by"
                          :da "Sortér via"}
   ::search/ascending    {:en "▲ ascending"
                          :da "▲ opadgående"}
   ::search/descending   {:en "▼ descending"
                          :da "▼ nedadgående"}
   ::search/explanation  {:en [:<>
                               [:h2 "Find documents"]
                               [:p "Use this page to search for relevant documents in our archive."]
                               [:ul
                                [:li
                                 "Results are found by matching document metadata to "
                                 [:strong [:em "one or more"]] " search criteria."]
                                [:li
                                 "The search criteria comprise: "
                                 [:em "archive, people, places, organisations, publications, languages,"] " and "
                                 [:em "terms"] "."]
                                [:li
                                 "Note that " [:strong [:em "all"]]
                                 " selected criteria will apply to every document in a search result."]
                                [:li
                                 "By default, a search criterion will be compared to anything. "
                                 "However, a particular field may be selected for any criterion."]
                                [:li
                                 "The search results may also be sorted according to a specific field. "
                                 "They can be further restricted to a certain range too."]]]
                          :da [:<>
                               [:h2 "Find dokumenter"]
                               [:p "Brug denne side til at søge efter relevante dokumenter i arkivet."]
                               [:ul
                                [:li
                                 "Resultater fås ved at matche dokumenters metadata med "
                                 [:strong [:em "et eller flere"]] " søgekriterier."]
                                [:li
                                 "Søgekriterier består af: "
                                 [:em "arkiv, personer, steder, organisationer, udgivelser, sprog "] " og "
                                 [:em "begreber"] "."]
                                [:li
                                 "Bemærk at " [:strong [:em "alle"]]
                                 " valgte kriterier gælder for hvert dokument i et søgeresultat."]
                                [:li
                                 "Som grundregel vil et søgekriterie blive sammenlignet med alt. "
                                 "Man kan dog også vælge et specifikt felt for ethvert kriterie."]
                                [:li
                                 "Søgeresultaterne kan også sorteres via visse af felterne. "
                                 "De kan også yderligere begrænses til en udvalgt del."]]]}
   ::search/explanation+ {:en [:p
                               "Currently, a total of " [:arg 0]
                               " entities may be used as search criteria. "
                               "An example of an entity that can be used as a criterion might be \""
                               [:arg 1] "\". "]
                          :da [:p
                               "I øjeblikket kan i alt " [:arg 0]
                               " entiteter bruges som søgekriterier. "
                               "Et eksempel på en entitet, der kan bruges som kriterie, kunne være \""
                               [:arg 1] "\". "]}})

(def bookmarks-page-translations
  {::bookmarks/other        {:en "Other"
                             :da "Andet"}
   ::bookmarks/encyclopedia {:en "Encyclopedia"
                             :da "Encyklopædi"}
   ::bookmarks/searches     {:en "Searches"
                             :da "Søgninger"}
   ::bookmarks/documents    {:en "Documents"
                             :da "Dokumenter"}
   ::bookmarks/go-caption   {:en "Go to this page"
                             :da "Gå til denne side"}
   ::bookmarks/empty        {:en (str "You do not seem to have bookmarked any pages. "
                                      "Click on the star in the top-right corner to bookmark a page.")
                             :da (str "Det ser ikke ud til, at du har tilføjet nogen bogmærker. "
                                      "Klik på stjernen i det øverste højre hjørne for at tilføje den nuværende side som bogmærke.")}})

(def other-translations
  {:entity.type/person         {:en "Person"
                                :da "Person"}
   :entity.type/language       {:en "Language"
                                :da "Sprog"}
   :entity.type/century        {:en "Century"
                                :da "Århundrede"}

   :file/name                  {:en "file"
                                :da "fil"}
   :file/extension             {:en "file extension"
                                :da "filendelse"}

   :document/century           {:en "century"
                                :da "århundrede"}
   :document/title             {:en "title"
                                :da "titel"}
   :document/author            {:en "author"
                                :da "forfatter"}
   :document/date              {:en "date"
                                :da "dato"}
   :document/year              {:en "år"
                                :da "år"}
   :document/language          {:en "language"
                                :da "sprog"}
   :document/notes             {:en "notes"
                                :da "noter"}
   :document/condition         {:en "condition"
                                :da "tilstand"}
   :document/facsimile         {:en "facsimile"
                                :da "facsimile"}

   :any                        {:en "any role"
                                :da "enhver rolle"}
   :exactly                    {:en "text"
                                :da "tekst"}

   ;; Some captions in use
   ::fshared/title-caption     {:en "View in the reader"
                                :da "Vis i læseren"}
   ::fshared/condition-caption {:en "Find documents in this condition"
                                :da "Find dokumenter i denne tilstand"}
   ::fshared/entity-caption    {:en "Find documents with this entity"
                                :da "Find relevante dokumenter for denne entitet"}})

(defn- into-dicts
  "Turn aligned translations into top-level `dicts` as expected by Tongue."
  [dicts [k {:keys [en da]}]]
  (merge-with merge dicts {:en {k en}
                           :da {k da}}))

(def dicts
  (reduce into-dicts {} (merge frontend-translations
                               privacy-page-translations
                               reader-page-translations
                               search-page-translations
                               bookmarks-page-translations
                               other-translations)))

(def tr
  (tongue/build-translate dicts))

(def tr-da
  (partial tr :da))

(def tr-en
  (partial tr :en))

(defn ->tr
  "Return a translation function/reagent component translating into da/en."
  []
  (if (= "da" @state/language)
    tr-da
    tr-en))
