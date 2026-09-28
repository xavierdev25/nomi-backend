"""D3 (accesibilidad para consulta) y métricas complementarias (DISENO_POSTEST.md §12.3 y §12.4)."""

from __future__ import annotations

from typing import Any

from . import config, cuentas, db, estado, net

# Misma regla que DietaryRestriction.isSatisfiedBy en el backend.
_SATISFIED_BY = {
    "VEGETARIANO": {"VEGETARIANO", "VEGANO"},
    "VEGANO": {"VEGANO"},
    "SIN_GLUTEN": {"SIN_GLUTEN"},
    "SIN_LACTOSA": {"SIN_LACTOSA", "VEGANO"},
}


def restrictions_of(user: dict[str, Any]) -> list[str]:
    return sorted({r.strip().upper() for r in (user.get("restrictions") or []) if r and r.strip().upper() != "NINGUNA"})


def compatible(tags: list[str] | None, restrictions: list[str]) -> bool:
    tagset = set(tags or [])
    return all(tagset & _SATISFIED_BY.get(r, {r}) for r in restrictions)


def accessibility(email: str) -> dict[str, Any]:
    """Conteos de D3 en este instante, con la misma consulta que usa la pantalla de la tienda."""
    t1 = estado.store_id(config.T1_NAME)
    user = cuentas.user(email)
    restrictions = restrictions_of(user)

    products = db.query(f"""
        SELECT p.id, p.nombre, p.stock, p.activo, p.disponible, p.etiquetas_dieteticas AS tags, s.activo AS store_activa
        FROM products p JOIN stores s ON s.id = p.store_id
        WHERE p.store_id = {t1} AND p.deleted_at IS NULL""")
    offered = [p for p in products if p["activo"]]
    unpublished = [p for p in products if not p["activo"]]
    sql_visible = [p for p in offered if p["disponible"]]
    purchasable = [p for p in sql_visible if p["stock"] >= 1 and p["store_activa"]]
    out_of_stock = [p for p in offered if p["stock"] == 0]
    compatible_list = [p for p in purchasable if compatible(p["tags"], restrictions)]
    disliked = {row["product_id"] for row in db.query(
        f"SELECT product_id FROM ai_recommendation_feedback WHERE user_id = {user['id']} AND liked = false")}
    recommendable = [p for p in compatible_list if p["id"] not in disliked][:40]

    response = net.backend("GET", f"/products/search?storeId={t1}&disponible=true&size=50",
                           token=cuentas.token(email))
    api_visible = response.body.get("totalElements") if response.status == 200 and isinstance(response.body, dict) else None

    # D3 se mide con la API que usa la app. Si no responde, los campos quedan vacíos: sustituirlos
    # por el conteo SQL daría un valor que parece medido y no lo es.
    visible = api_visible
    return {
        "store_id": t1,
        "total_products_offered": len(offered),
        "accessible_products": visible,
        "visible_products": visible,
        "accessible_sql_check_ok": (api_visible == len(sql_visible)) if visible is not None else None,
        "accessibility_rate": round(visible / len(offered) * 100, 2) if offered and visible is not None else None,
        "purchasable_products": len(purchasable),
        "out_of_stock_products": len(out_of_stock),
        "hidden_products": len(offered) - visible if visible is not None else None,
        "unpublished_products": len(unpublished),
        "compatible_products": len(compatible_list),
        "recommendable_products": len(recommendable),
        "discarded_by_restrictions": len(purchasable) - len(compatible_list),
        "restrictions": restrictions,
        "catalog_api_status": response.status,
    }


def tags_by_product(product_ids: list[int]) -> dict[int, list[str]]:
    if not product_ids:
        return {}
    rows = db.query(f"SELECT id, etiquetas_dieteticas AS tags FROM products WHERE id IN ({', '.join(map(str, product_ids))})")
    return {row["id"]: row["tags"] or [] for row in rows}
