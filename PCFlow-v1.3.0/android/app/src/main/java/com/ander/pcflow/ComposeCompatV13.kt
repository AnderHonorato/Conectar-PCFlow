package com.ander.pcflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Compatibilidade local para manter o shell V1.3 independente de detalhes de versão
 * do Compose presentes no projeto alpha.
 */
@Composable
fun <T> rememberSaveable(calculation: () -> T): T = remember(calculation = calculation)

/**
 * Fallback usado apenas quando um componente reutilizável chama weight fora de um
 * RowScope/ColumnScope. Dentro de Row/Column, a extensão oficial do Compose continua
 * tendo precedência por estar disponível no receiver do escopo.
 */
fun Modifier.weight(weight: Float): Modifier = this
