package com.example.arsens.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.arsens.data.*
import kotlin.math.abs

@Composable
internal fun WorkflowDimensionComparison(values: List<DimensionComparison>, limit: Int) {
    if (values.isEmpty()) return
    Text("STL ↔ scan",style=MaterialTheme.typography.titleSmall)
    Row { Text("Maat",Modifier.weight(1f));Text("STL",Modifier.weight(1f));Text("Scan",Modifier.weight(1f));Text("Δ mm",Modifier.weight(1f)) }
    values.forEach { value -> Row {
        Text(if(value.axis=="x") "Lengte" else "Breedte",Modifier.weight(1f))
        Text("${value.stlMm}",Modifier.weight(1f));Text("${value.scanMm}",Modifier.weight(1f))
        Text("${if(value.deltaMm>0) "+" else ""}${value.deltaMm}",Modifier.weight(1f),
            color=if(abs(value.deltaMm)>limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    } }
    if(values.any { abs(it.deltaMm)>limit }) Text("Verschil groter dan $limit mm · controleer de maatbron",color=MaterialTheme.colorScheme.error)
}
