package org.jf.baksmali.Adaptors

import org.jf.baksmali.Adaptors.Format.IfMethodItem
import org.jf.baksmali.Adaptors.Format.InstructionMethodItem
import org.jf.baksmali.Adaptors.Format.OffsetInstructionFormatMethodItem
import org.jf.dexlib.Code.Opcode
import java.util.*
import kotlin.collections.ArrayList


data class LabelInfo(val returnLabel: LabelMethodItem?, val labelUsageCounts: MutableMap<Int, Int>) {
    fun usageCount(labelMethodItem: LabelMethodItem): Int {
        return labelUsageCounts.getOrDefault(labelMethodItem.labelAddress, -1)
    }

    fun decrementCount(labelMethodItem: LabelMethodItem) {
        labelUsageCounts[labelMethodItem.labelAddress] = labelUsageCounts[labelMethodItem.labelAddress]!! - 1
    }
}


fun recoverControlFlow(methodItems: MutableList<MethodItem>) {
    // One run through to get the number of times each labelled is referred to and find return label
    val labelInfo = calculateLabelInfo(methodItems)

    // One run through to handle If's that jump somewhere else then jump back
    transformOutAndBackIfs(methodItems, labelInfo)

    // Another run through to handle If's that jump a bit further in the current path
    transformForwardSkipIfs(methodItems, labelInfo)
}

private fun calculateLabelInfo(methodItems: List<MethodItem>): LabelInfo {
    var returnLabel: LabelMethodItem? = null
    val labelUsageCounts = mutableMapOf<Int, Int>()
    for (i in methodItems.indices) {
        val methodItem = methodItems[i]
        if (methodItem is OffsetInstructionFormatMethodItem<*>) {
            val label = methodItem.getLabel().labelAddress
            labelUsageCounts[label] = labelUsageCounts.getOrDefault(label, 0) + 1
        }
        if (methodItem is LabelMethodItem) {
            if (methodItem.labelPrefix == "goto_") {
                // See if any other instruction at this address is a return
                var j = i + 1
                while (j < methodItems.size && methodItems[j].getCodeAddress() == methodItem.getCodeAddress()) {
                    val maybeReturnItem = methodItems[j]
                    if (maybeReturnItem is InstructionMethodItem<*>) {
                        if (maybeReturnItem.instruction.opcode.value in 0x0e..0x011) {
                            if (returnLabel != null) {
                                throw RuntimeException("Found multiple return labels! " + returnLabel.get() + " and " + methodItem.get())
                            }
                            returnLabel = methodItem
                            break
                        }
                    }
                    j++
                }
            }
        }
    }
    return LabelInfo(returnLabel, labelUsageCounts)
}

private fun transformOutAndBackIfs(methodItems: MutableList<MethodItem>, labelInfo: LabelInfo) {
    // Work backwards so we don't have to worry about scanning the contents of an If after we create it.
    for (i in methodItems.indices.reversed()) {
        val methodItem = methodItems[i]
        if (methodItem is OffsetInstructionFormatMethodItem<*>) {
            val label = methodItem.getLabel()
            if (methodItem.instruction.opcode.value in 0x032..0x03d && labelInfo.usageCount(label) == 1) {
                val (labelIndex, finalGotoIndex) = findForwardIfBlockBoundaries(i, methodItems, methodItem, labelInfo)
                if (labelIndex != -1 && finalGotoIndex != -1) {
                    // Replace the current instruction with an If
                    pullBlockIntoIf(
                            methodItems,
                            labelIndex,
                            finalGotoIndex,
                            i,
                            IfMethodItem.emptyFromOffsetInstruction(methodItem),
                            putInThen = true
                    )
                    // We've just consumed one usage of that label, go ahead and reduce its usage count.
                    labelInfo.decrementCount(label)
                }
            }
        }
    }
}

private fun findForwardIfBlockBoundaries(startIndex: Int, methodItems: List<MethodItem>, methodItem: OffsetInstructionFormatMethodItem<*>, labelInfo: LabelInfo): Pair<Int, Int> {
    // Find the label this goes to and the goto that brings it back (if any)
    var labelIndex = -1
    var finalGotoIndex = -1
    for (i in startIndex until methodItems.size) {
        val forwardItem = methodItems[i]
        if (labelIndex == -1) {
            if (forwardItem is LabelMethodItem && forwardItem === methodItem.getLabel()) {
                val prevItem = methodItems[i - 1]
                // Make sure we can't fall through to this label
                if (prevItem is InstructionMethodItem<*> && !prevItem.instruction.opcode.canContinue()) {
                    labelIndex = i
                } else {
                    // We can't be sure that the only entry point to this code is from the if, so we have to bail.
                    break
                }
            }
        } else {
            // We should either see a valid ending non-conditional goto, or we might see something that
            // invalidates this block.
            if (forwardItem is InstructionMethodItem<*>) {
                val value = forwardItem.instruction.opcode.value
                if (value in 0x028..0x02a && forwardItem !== labelInfo.returnLabel) {
                    // This is an unconditional goto. Record the index and break
                    finalGotoIndex = i
                    break
                } else if (value == Opcode.THROW.value || value in 0x02b..0x02c) {
                    // If this is a throw or switch, then we're encountering other types of control
                    // flow, so bail.
                    break
                }
            }
            if (forwardItem is LabelMethodItem) {
                if (forwardItem !== labelInfo.returnLabel && labelInfo.usageCount(forwardItem) != 0) {
                    // We've run into a label that isn't a return and still has things going to it, which
                    // is an entry point into this code, our analysis logic may no longer be sound. Clear
                    // the pending ifs.
                    // TODO RIGHT NOW - don't break if all references to the label are within the section we're looking at.
                    break
                }
            }
        }
    }
    return Pair(labelIndex, finalGotoIndex)
}

private data class InProgressIf(val ifMethodItem: IfMethodItem, val targetAddress: Int, val startIndex: Int)

private fun transformForwardSkipIfs(
        methodItems: MutableList<MethodItem>,
        labelInfo: LabelInfo
) {
    val inProgressIfs = Stack<InProgressIf>()
    var i = 0
    while (i < methodItems.size) {
        val methodItem = methodItems[i]
        if (methodItem is IfMethodItem) {
            // Get the next label, if any
            var labelMethodItem: LabelMethodItem? = null
            if (i + 1 < methodItems.size) {
                val nextItem = methodItems[i + 1]
                if (nextItem is LabelMethodItem) {
                    labelMethodItem = nextItem
                } else if (nextItem is OffsetInstructionFormatMethodItem<*>) {
                    labelMethodItem = nextItem.getLabel()
                }
            }
            for (items in listOf(methodItem.thenItems, methodItem.elseItems)) {
                if (labelMethodItem != null) {
                    items.add(labelMethodItem)
                }
                transformForwardSkipIfs(items, labelInfo)
                if (labelMethodItem != null) {
                    if (items.last() != labelMethodItem) {
                        throw RuntimeException("Expected items to end with label, actually got: ${items.last()}")
                    }
                    items.removeAt(items.size - 1)
                }
            }

            val lastItemGotoAddress = getGotoAddress(methodItem.thenItems.lastOrNull())
            if (methodItem.elseItems.isEmpty() && lastItemGotoAddress != null) {
                inProgressIfs.add(InProgressIf(methodItem, lastItemGotoAddress, i))
            }
        } else if (methodItem is InstructionMethodItem<*>) {
            val value = methodItem.instruction.opcode.value
            if (value in 0x032..0x03d && methodItem is OffsetInstructionFormatMethodItem<*>) {
                inProgressIfs.add(InProgressIf(
                        IfMethodItem.emptyFromOffsetInstruction(methodItem),
                        methodItem.getLabel().labelAddress,
                        i
                ))
            } else if (value in 0x028..0x02a) {
                // If this is a goto, then we'll treat it as if it is that label.
                val gotoItem = methodItem as OffsetInstructionFormatMethodItem<*>
                i = processInProgressIfs(inProgressIfs, gotoItem.getLabel(), methodItems, i, labelInfo)
                // However, for anything that wasn't going to this label, this is now an unsupported control flow, so
                // clear the remaining If's.
                inProgressIfs.clear()
            } else if (value == Opcode.THROW.value || value in 0x02b..0x02c) {
                // If this is a throw or switch, then we're encountering other types of control
                // flow, so bail on all potential If's we've seen so far.
                inProgressIfs.clear()
            }
        } else if (methodItem is LabelMethodItem) {
            i = processInProgressIfs(inProgressIfs, methodItem, methodItems, i, labelInfo)

            if (methodItem !== labelInfo.returnLabel && labelInfo.usageCount(methodItem) != 0) {
                // We've run into a label that isn't a return and still has things going to it, which is an entry point
                // into this code, our analysis logic may no longer be sound. Clear the pending ifs.
                inProgressIfs.clear()
            } else if (/*methodItem !== effectiveEndLabel && */methodItem !== labelInfo.returnLabel) {
                // This is a label where we've already handled all things that go to it. Don't render this label,
                // because we shouldn't be printing out out anything that goes to it.
                methodItems.removeAt(i)
                i--
            }
        }
        i++
    }
}

private fun processInProgressIfs(inProgressIfs: Stack<InProgressIf>, methodItem: LabelMethodItem, methodItems: MutableList<MethodItem>, currentIndex: Int, labelInfo: LabelInfo): Int {
    var i = currentIndex
    val lowestPositionLookingForLabel = getLowestPositionLookingForLabel(inProgressIfs, methodItem)
    while (inProgressIfs.size > lowestPositionLookingForLabel) {
        val (ifItem, targetAddress, startIndex) = inProgressIfs.pop()

        if (targetAddress != methodItem.labelAddress) {
            continue
        }

        pullBlockIntoIf(
                methodItems,
                startIndex + 1,
                i - 1,
                startIndex,
                ifItem,
                putInThen = false
        )
        // Reset our iteration index to just after updated item, which should be the label we were just
        // handling, so we can continue to process If's for that label and/or process the next item on our next loop.
        i = startIndex + 1
        // We've just consumed one usage of that label, go ahead and reduce its usage count.
        labelInfo.decrementCount(methodItem)
    }
    return i
}

private fun pullBlockIntoIf(
        methodItems: MutableList<MethodItem>,
        startIndexInclusive: Int,
        endIndexInclusive: Int,
        ifBlockIndex: Int,
        originalItem: IfMethodItem,
        putInThen: Boolean
) {
    // We leave the original item in place, since we're going to replace it with an If item. Also
    // leave the label in place.
    val subList = methodItems.subList(startIndexInclusive, endIndexInclusive + 1)
    // Copy the sublist, then clear it to remove these items from the main list
    val itemsCopy = ArrayList(subList)
    subList.clear()


    val (thenItems, elseItems, existingItems) = if (putInThen) {
        Triple(itemsCopy, originalItem.elseItems, originalItem.thenItems)
    } else {
        Triple(originalItem.thenItems, itemsCopy, originalItem.elseItems)
    }

    if (existingItems.isNotEmpty()) {
        throw RuntimeException("Got non-empty existing items - putInThen=$putInThen existingItems=$existingItems")
    }

    // Update the If item
    val ifMethodItem = originalItem.withNewItems(thenItems, elseItems)
    // Replace the original offset item
    methodItems[ifBlockIndex] = ifMethodItem
    // Remove the redundant ending gotos
    if (ifBlockIndex + 1< methodItems.size) {
        removeRedundantEndingGotos(ifMethodItem, methodItems[ifBlockIndex + 1].codeAddress)
    }
}

private fun removeRedundantEndingGotos(ifMethodItem: IfMethodItem, nextInstructionAddress: Int) {
    for (items in listOf(ifMethodItem.thenItems, ifMethodItem.elseItems)) {
        val lastItem = items.lastOrNull()
        if (getGotoAddress(lastItem) == nextInstructionAddress) {
            items.removeAt(items.size - 1)
        } else if (lastItem is IfMethodItem) {
            removeRedundantEndingGotos(lastItem, nextInstructionAddress)
        }
    }
}

private fun getLowestPositionLookingForLabel(inProgressIfs: Stack<InProgressIf>, labelMethodItem: LabelMethodItem): Int {
    val i = inProgressIfs.indexOfFirst { e -> e.targetAddress == labelMethodItem.labelAddress }
    return if (i == -1) {
        inProgressIfs.size
    } else {
        i
    }
}

private fun getGotoAddress(methodItem: MethodItem?): Int? {
    val offsetItem = methodItem as? OffsetInstructionFormatMethodItem<*>
    return if (offsetItem != null && offsetItem.instruction.opcode.value in 0x028..0x02a) {
        offsetItem.getLabel().labelAddress
    } else {
        null
    }
}