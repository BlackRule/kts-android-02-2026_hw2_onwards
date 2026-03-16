package com.example.myapplication.feature.itemCatalog.model

import myapplication.composeapp.generated.resources.Res
import myapplication.composeapp.generated.resources.unit_kg_label
import myapplication.composeapp.generated.resources.unit_piece_label
import org.jetbrains.compose.resources.StringResource

fun UnitType.labelResource(): StringResource {
    return when (this) {
        UnitType.PIECE -> Res.string.unit_piece_label
        UnitType.KG -> Res.string.unit_kg_label
    }
}
