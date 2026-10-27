from html import escape
from pathlib import Path

OUT = Path(__file__).with_name("TOQ_C4_Schema_Actuel.svg")
parts = []

def add(s):
    parts.append(s)

def txt(x, y, value, size=25, color="#153d36", weight=400, anchor="start", cls=""):
    add(f'<text x="{x}" y="{y}" fill="{color}" font-family="Arial, Helvetica, sans-serif" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}" class="{cls}">{escape(value)}</text>')

def rect(x, y, w, h, fill, stroke="#d8e1dc", radius=20, sw=2, dash=""):
    ds = f' stroke-dasharray="{dash}"' if dash else ""
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"{ds}/>')

def line(d, color="#67887c", width=3, dash="", marker=True):
    ds = f' stroke-dasharray="{dash}"' if dash else ""
    mk = ' marker-end="url(#arrow)"' if marker else ""
    add(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"{ds}{mk}/>')

def card(x,y,w,h,title,sub,fill,stroke,accent,tag=None):
    rect(x,y,w,h,fill,stroke,18,2.5)
    rect(x,y,8,h,accent,accent,4,0)
    txt(x+30,y+40,title,27,"#173b33",700)
    for i,s in enumerate(sub):
        txt(x+30,y+70+i*25,s,19,"#466257")
    if tag:
        tw=max(88,len(tag)*11+24)
        rect(x+w-tw-14,y+13,tw,31,"#e8f2eb","#e8f2eb",10,0)
        txt(x+w-tw/2-14,y+35,tag,15,"#236750",700,"middle")

add('''<svg xmlns="http://www.w3.org/2000/svg" width="2400" height="1510" viewBox="0 0 2400 1510">
<defs>
  <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="9" markerHeight="9" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 Z" fill="#67887c"/></marker>
  <filter id="shadow" x="-5%" y="-5%" width="110%" height="115%"><feDropShadow dx="0" dy="8" stdDeviation="12" flood-color="#12382e" flood-opacity="0.08"/></filter>
</defs>
<rect width="2400" height="1510" fill="#f7f6ef"/>
<rect x="0" y="0" width="2400" height="16" fill="#07533d"/>
''')
txt(68,75,"TOQ · Architecture actuelle",48,"#064d39",700)
txt(69,115,"Vue C4 Container · lecture du code et de Docker Compose · 01/10/2026",23,"#63766d")

# Zones: Docker boundary, one Postgres instance, external dependencies.
rect(350,160,1240,1155,"#fcfdf9","#9ab5a7",24,3,"12 8")
txt(380,201,"MACHINE DE DÉVELOPPEMENT · DOCKER COMPOSE",25,"#17624b",700)
txt(380,232,"HTTP local · services internes non exposés",18,"#678579")
rect(1145,195,395,1068,"#f0f5f1","#9fb9ac",22,2,"9 7")
txt(1175,229,"PostgreSQL",28,"#155441",700)
txt(1175,252,"1 conteneur · 6 bases logiques",17,"#5b7569")

rect(1680,160,655,775,"#fffdf8","#c9d7ce",24,2)
txt(1710,207,"SERVICES EXTERNES",28,"#155441",700)
txt(1710,239,"Appels sortants depuis les services",19,"#64776c")

rect(1680,960,655,355,"#f0f5f1","#c4d5c9",24,2)
txt(1710,1005,"ÉCARTS AVEC LE SCHÉMA IMAGINÉ",25,"#155441",700)
for yy,s in [(1049,"Courses → intégrées dans Planner Service"),
             (1093,"IA → intégrée dans User Service"),
             (1137,"Libraries → modules internes, non déployés"),
             (1181,"Strava, Lidl, RabbitMQ, Grafana : absents"),
             (1225,"CIQUAL : données importées localement")]:
    txt(1710,yy,"• "+s,20,"#345b4a")
txt(1710,1268,"Aucun serveur ni staging distant dans cette vue.",18,"#698074")

# User and browser outside Docker.
card(65,260,245,112,"Utilisateur",["Navigue, s'inscrit", "et utilise TOQ"],"#e5f4ed","#89c1a3","#119262")
card(65,478,245,156,"Navigateur",["React + TypeScript", "SPA React / Vite", "OIDC avec Keycloak"],"#e4eff7","#9fc1d6","#2789b1")
line("M188 372 L188 476", "#67887c",3)
txt(207,422,"usage",17,"#668075")

# Front-end edge container.
card(380,510,270,204,"Frontend Edge",["Nginx :8080", "Fichiers React / Vite", "Proxy /api/v1/*", "5 services REST"],"#fff8e8","#e3c992","#d6a13e")

# Keycloak + service rows.
rows=[
    (260,"Keycloak",["Identité, login/register", "OIDC, JWT / JWKS"],"keycloak",["Comptes, rôles", "et sessions"],"#e9edfb","#7c93d5"),
    (425,"User Service",["Profil, préférences, objectifs", "Agent TOQi / Gemini"],"toq_user",["Profil, objectifs", "et préférences"],"#e8f5ec","#3f9c6d"),
    (590,"Recipe Service",["Recettes, ingrédients", "Imports TheMealDB"],"toq_recipe",["Recettes, ingrédients", "et images"],"#e8f5ec","#3f9c6d"),
    (755,"Nutrition Service",["Suivi, aliments, calculs", "CIQUAL local + OFF"],"toq_nutrition",["Référentiel CIQUAL", "et suivi nutritionnel"],"#fff0df","#d9984d"),
    (920,"Planner Service",["Planning des repas", "Listes de courses"],"toq_planner",["Planning et listes", "de courses"],"#fff0df","#d9984d"),
    (1085,"Social Service",["Publications, commentaires", "Favoris et partages"],"toq_social",["Publications", "et interactions"],"#f1eaf8","#ac81c1")
]
for y,name,subs,dbname,dbsubs,fill,accent in rows:
    card(775,y,315,118,name,subs,fill,accent,accent)
    card(1180,y+4,325,110,dbname,dbsubs,"#ffffff","#b6c9bc",accent)
    line(f"M1092 {y+59} L1175 {y+59}","#67887c",3)

# Request flow via Nginx to five Spring services. Keycloak separately via browser.
line("M310 556 L374 556", "#4b8c72",4)
txt(320,543,"HTTP",16,"#397b61")
line("M652 613 L708 613 L708 1144", "#4b8c72",4,"",False)
line("M708 484 L770 484", "#4b8c72",3)
line("M708 649 L770 649", "#4b8c72",3)
line("M708 814 L770 814", "#4b8c72",3)
line("M708 979 L770 979", "#4b8c72",3)
line("M708 1144 L770 1144", "#4b8c72",3)
txt(655,748,"/api/v1/*",17,"#397b61",700,"middle")

# Browser direct to Keycloak for OIDC/PKCE.
line("M310 494 L332 494 L332 390 L675 390 L675 319 L770 319", "#6d85bf",3,"9 7")
txt(416,382,"OIDC / PKCE → :8081",18,"#526daa",700)

# External API cards and lower-gap connectors, avoiding the DB cards.
external=[
    (425,500,"Gemini API",["Agent TOQi", "Clé côté backend uniquement"],"#eee9fa","#9c86c9"),
    (590,665,"TheMealDB",["Recherche et import", "de recettes en ligne"],"#e4f0f8","#78b4d2"),
    (755,815,"Open Food Facts",["Produits et valeurs", "nutritionnelles"],"#e4f0f8","#78b4d2")
]
for y,ey,title,sub,fill,accent in external:
    # Route within the 47 px gap below the database row.
    line(f"M1060 {y+118} L1060 {y+140} L1610 {y+140} L1610 {ey+55} L1715 {ey+55}",accent,3,"7 6")
    card(1720,ey,535,110,title,sub,fill,accent,accent)

# Legend and implementation notes at bottom.
rect(65,1350,2270,112,"#ffffff","#d6e1da",20,2)
txt(96,1393,"Légende",22,"#155441",700)
line("M210 1383 L292 1383","#67887c",3)
txt(308,1391,"SQL / REST local",19,"#4b6257")
line("M600 1383 L682 1383","#6d85bf",3,"9 7")
txt(698,1391,"Authentification",19,"#4b6257")
line("M1020 1383 L1102 1383","#9c86c9",3,"7 6")
txt(1118,1391,"API externe",19,"#4b6257")
txt(96,1430,"Les 5 API sont des conteneurs Java / Spring Boot. Elles vérifient les JWT Keycloak. Les appels entre services sont listés juste au-dessus.",19,"#596e61")
txt(65,1329,"Appels internes : User → Recipe, Planner · Nutrition → Recipe · Planner → Recipe · Social → User, Recipe",20,"#406252")
add('</svg>')
OUT.write_text("\n".join(parts),encoding="utf-8")
print(OUT)
