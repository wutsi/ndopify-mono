# Parcours agent — Partner Central

Stage 1 (neo-user-journey). Sources: Partner Central doc, pitch, vision/mission. Language of UI: French.

## Persona

**Agent immobilier indépendant (Yaoundé / Douala).** Mobile first, Mobile Money au quotidien, connexion variable.
Préoccupations (pitch) : clients contournés après une visite, visites non rémunérées, conflits entre agents,
commissions floues. Méfiant envers les plateformes qui « prennent leur part ».

| Besoin | Réponse Ndopify (chiffres du pitch) |
|---|---|
| Des clients sérieux | Locataires vérifiés, pass de visite payé en Mobile Money |
| Être payé pour se déplacer | Indemnité de **1 500 FCFA / visite** garantie |
| Gagner sur chaque location | Commission, **70 %** conservés sur transaction directe |
| Ne pas se faire sauter | Anti-sautage : la relation client est tracée dans la plateforme |

## Parcours

| # | Étape | Page | Action | Émotion | Risque d'abandon | Réponse design |
|---|---|---|---|---|---|---|
| 1 | Découverte | `index.html` | Lit les 4 avantages | Curieux, méfiant | Promesse trop vague | Avantages concrets avec chiffres, un seul CTA |
| 2 | Identification | `index.html` | Saisit son email | Neutre | Formulaire long | 1 seul champ ; l'email décide : compte existant → OTP, sinon → inscription |
| 3a | Inscription | `https://www.ndopify.com/joindre.html` (site public) | Crée son compte agent | Engagé, prudent | Trop de champs | Redirection vers le parcours d'inscription existant |
| 3b | Connexion OTP | `connexion.html` | Saisit le code à 6 chiffres | Impatient | Code non reçu / expiré (5 min) | Compte à rebours, renvoi du code, limitation des tentatives expliquée |
| 4 | Statut du compte | `tableau-de-bord.html` | Voit « Non vérifié » | Frustré si bloqué | Ne comprend pas pourquoi | Message clair : sans vérification, pas d'accès aux services ; CTA unique « Vérifier mon identité » |
| 5 | KYC | `verification.html` | Téléverse sa CNI (recto + verso) | Anxieux | Photo rejetée, fichier > 5 Mo | Repères visuels recto/verso, règles (lisible, sans reflet, non expiré) expliquées avant l'envoi |
| 6 | Confirmation | `confirmation.html` | Attend | Soulagé, impatient | Silence | Délai 24 h, notification email + SMS annoncée, motif de rejet + nouvelle soumission |
| 7 | Espace agent | `espace-agent.html` | Visites, commissions, clients, anti-sautage | Confiant | — | 4 modules, état « Vérifié » |

## États du compte (tableau de bord)

Étapes du suivi : `Soumission des documents` → `Vérification` → `Vérifié` (en cas de rejet : retour à `Soumission des documents`, avec le motif).

## Règles de microcopy

Tutoiement exclu, vouvoiement chaleureux. Phrases courtes, verbes d'action, aucun jargon (« pièce d'identité », pas « KYC »
côté utilisateur, sauf mention secondaire). Erreurs : dire ce qui s'est passé + quoi faire.

## Écarts à valider avec le produit

- Le doc source écrit « Ndapify » : on utilise « Ndopify » (DESIGN.md, repo).
- Le doc ne décrit pas les champs d'inscription : proposition minimale (nom, prénom, email, téléphone, ville).
- Pas de mot de passe : l'authentification est uniquement par OTP email (le doc mentionne « mot de passe / code OTP »,
  on retient l'OTP).
- Le doc n'a pas de détail pour les 4 modules de l'espace agent : pages de présentation/état vide uniquement.
