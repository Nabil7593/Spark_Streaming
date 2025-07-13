import streamlit as st
import pandas as pd
import plotly.express as px
from sqlalchemy import create_engine

# Auto-refresh toutes les 30 sec
st.markdown("""
    <script>
    setTimeout(() => window.location.reload(), 30000)
    </script>
    """, unsafe_allow_html=True)

# Réduire le padding top
st.markdown("""
    <style>
      .block-container { padding-top: 0.5rem; }
    </style>
    """, unsafe_allow_html=True)

st.set_page_config(page_title="Dashboard Dépôts bancaires", layout="wide")

# Connexion
engine = create_engine("postgresql://admin:admin@postgres:5432/sparkdb")

@st.cache_data(ttl=30)
def load_table(table):
    return pd.read_sql(f"SELECT * FROM {table}", engine)

# 1️⃣ Indicateur de volume brut
df_raw = load_table("bank_data")
st.subheader("Nombre total de lignes brutes")
st.metric(label="", value=f"{df_raw.shape[0]:,d}")

st.markdown("---")

# 2️⃣ Charger les autres tables
df_job     = load_table("deposit_by_job")
df_marital = load_table("deposit_by_marital")
df_edu     = load_table("deposit_by_education")
df_contact = load_table("yes_rate_by_contact")
df_housing = load_table("deposit_by_housing")

# 3️⃣ Construire les graphiques Plotly
fig1 = px.pie(df_marital, names="marital", values="nb_depots",
              title="Dépôts “yes” par situation familiale")

fig2 = px.bar(df_job.sort_values("nb_depots", ascending=False),
              x="nb_depots", y="job", orientation="h",
              title="Dépôts “yes” par métier")

fig3 = px.pie(df_edu, names="education", values="nb_depots",
              title="Dépôts “yes” par niveau d'éducation")

fig4 = px.bar(df_contact, x="contact", y="yes_rate",
              title="Taux de souscription “yes” par type de contact")

# Agréger housing avant de pivoter
dfh = (df_housing
      .groupby(["housing", "deposit"])["nb"]
      .sum()
      .unstack(fill_value=0))
fig5 = px.bar(dfh, barmode="stack",
              title="Distribution (yes/no) par type de logement")

# 4️⃣ Afficher en grille  (2 sur la première ligne, 3 sur la deuxième, par ex.)
row1 = st.columns(3)
row1[0].plotly_chart(fig1, use_container_width=True)
row1[1].plotly_chart(fig2, use_container_width=True)
row1[2].plotly_chart(fig3, use_container_width=True)

row2 = st.columns(2)
row2[0].plotly_chart(fig4, use_container_width=True)
row2[1].plotly_chart(fig5, use_container_width=True)

st.markdown("_Mis à jour automatiquement toutes les 30 s._")
