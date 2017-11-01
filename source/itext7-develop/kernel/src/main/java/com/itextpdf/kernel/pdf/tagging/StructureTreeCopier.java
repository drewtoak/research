/*

    This file is part of the iText (R) project.
    Copyright (c) 1998-2017 iText Group NV
    Authors: Bruno Lowagie, Paulo Soares, et al.

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation with the addition of the
    following permission added to Section 15 as permitted in Section 7(a):
    FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
    ITEXT GROUP. ITEXT GROUP DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
    OF THIRD PARTY RIGHTS

    This program is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License
    along with this program; if not, see http://www.gnu.org/licenses or write to
    the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
    Boston, MA, 02110-1301 USA, or download the license from the following URL:
    http://itextpdf.com/terms-of-use/

    The interactive user interfaces in modified source and object code versions
    of this program must display Appropriate Legal Notices, as required under
    Section 5 of the GNU Affero General Public License.

    In accordance with Section 7(b) of the GNU Affero General Public License,
    a covered work must retain the producer line in every PDF that is created
    or manipulated using iText.

    You can be released from the requirements of the license by purchasing
    a commercial license. Buying such a license is mandatory as soon as you
    develop commercial activities involving the iText software without
    disclosing the source code of your own applications.
    These activities include: offering paid services to customers as an ASP,
    serving PDFs on the fly in a web application, shipping iText with a closed
    source product.

    For more information, please contact iText Software Corp. at this
    address: sales@itextpdf.com
 */
package com.itextpdf.kernel.pdf.tagging;

import com.itextpdf.kernel.PdfException;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal helper class which is used to copy tag structure across documents.
 */
class StructureTreeCopier {

    private static List<PdfName> ignoreKeysForCopy = new ArrayList<PdfName>();

    private static List<PdfName> ignoreKeysForClone = new ArrayList<PdfName>();

    static {
        ignoreKeysForCopy.add(PdfName.K);
        ignoreKeysForCopy.add(PdfName.P);
        ignoreKeysForCopy.add(PdfName.Pg);
        ignoreKeysForCopy.add(PdfName.Obj);

        ignoreKeysForClone.add(PdfName.K);
        ignoreKeysForClone.add(PdfName.P);
    }

    /**
     * Copies structure to a {@code destDocument}.
     * <br/><br/>
     * NOTE: Works only for {@code PdfStructTreeRoot} that is read from the document opened in reading mode,
     * otherwise an exception is thrown.
     *
     * @param destDocument document to copy structure to. Shall not be current document.
     * @param page2page  association between original page and copied page.
     */
    public static void copyTo(PdfDocument destDocument, Map<PdfPage, PdfPage> page2page, PdfDocument callingDocument) {
        if (!destDocument.isTagged())
            return;

        copyTo(destDocument, page2page, callingDocument, false);
    }

    /**
     * Copies structure to a {@code destDocument} and insert it in a specified position in the document.
     * <br/><br/>
     * NOTE: Works only for {@code PdfStructTreeRoot} that is read from the document opened in reading mode,
     * otherwise an exception is thrown.
     * <br/>
     * Also, to insert a tagged page into existing tag structure, existing tag structure shouldn't be flushed, otherwise
     * an exception may be raised.
     *
     * @param destDocument       document to copy structure to.
     * @param insertBeforePage indicates where the structure to be inserted.
     * @param page2page        association between original page and copied page.
     */
    public static void copyTo(PdfDocument destDocument, int insertBeforePage, Map<PdfPage, PdfPage> page2page, PdfDocument callingDocument) {
        if (!destDocument.isTagged())
            return;

        // Here we separate the structure tree in two parts: struct elems that belong to the pages which indexes are
        // less then insertBeforePage and those struct elems that belong to other pages. Some elems might belong
        // to both parts and actually these are the ones that we are looking for.
        Set<PdfObject> firstPartElems = new HashSet<>();
        PdfStructTreeRoot destStructTreeRoot = destDocument.getStructTreeRoot();
        for (int i = 1; i < insertBeforePage; ++i) {
            PdfPage pageOfFirstHalf = destDocument.getPage(i);
            Collection<PdfMcr> pageMcrs = destStructTreeRoot.getPageMarkedContentReferences(pageOfFirstHalf);
            if (pageMcrs != null) {
                for (PdfMcr mcr : pageMcrs) {
                    firstPartElems.add(mcr.getPdfObject());
                    PdfDictionary top = addAllParentsToSet(mcr, firstPartElems);
                    if (top.isFlushed()) {
                        throw new PdfException(PdfException.TagFromTheExistingTagStructureIsFlushedCannotAddCopiedPageTags);
                    }
                }
            }
        }

        List<PdfDictionary> clonedTops = new ArrayList<>();
        PdfArray tops = destStructTreeRoot.getKidsObject();

        // Now we "walk" through all the elems which belong to the first part, and look for the ones that contain both
        // kids from first and second part. We clone found elements and move kids from the second part to cloned elems.
        int lastTopBefore = 0;
        for (int i = 0; i < tops.size(); ++i) {
            PdfDictionary top = tops.getAsDictionary(i);
            if (firstPartElems.contains(top)) {
                lastTopBefore = i;

                LastClonedAncestor lastCloned = new LastClonedAncestor();
                lastCloned.ancestor = top;
                PdfDictionary topClone = top.clone(ignoreKeysForClone);
                topClone.put(PdfName.P, destStructTreeRoot.getPdfObject());
                lastCloned.clone = topClone;

                separateKids(top, firstPartElems, lastCloned);

                if (topClone.containsKey(PdfName.K)) {
                    topClone.makeIndirect(destDocument);
                    clonedTops.add(topClone);
                }
            }
        }

        for (int i = 0; i < clonedTops.size(); ++i) {
            destStructTreeRoot.addKidObject(lastTopBefore + 1 + i, clonedTops.get(i));
        }

        copyTo(destDocument, page2page, callingDocument, false, lastTopBefore + 1);
    }

    /**
     * Copies structure to a {@code destDocument}.
     *
     * @param destDocument document to cpt structure to.
     * @param page2page  association between original page and copied page.
     * @param copyFromDestDocument indicates if <code>page2page</code> keys and values represent pages from {@code destDocument}.
     */
    private static void copyTo(PdfDocument destDocument, Map<PdfPage, PdfPage> page2page, PdfDocument callingDocument, boolean copyFromDestDocument) {
        copyTo(destDocument, page2page, callingDocument, copyFromDestDocument, -1);
    }

    private static void copyTo(PdfDocument destDocument, Map<PdfPage, PdfPage> page2page, PdfDocument callingDocument, boolean copyFromDestDocument, int insertIndex) {
        PdfDocument fromDocument = copyFromDestDocument ? destDocument : callingDocument;
        Set<PdfDictionary> tops = new HashSet<>();
        Set<PdfObject> objectsToCopy = new HashSet<>();
        Map<PdfDictionary, PdfDictionary> page2pageDictionaries = new HashMap<>();
        for (Map.Entry<PdfPage, PdfPage> page : page2page.entrySet()) {
            page2pageDictionaries.put(page.getKey().getPdfObject(), page.getValue().getPdfObject());
            Collection<PdfMcr> mcrs = fromDocument.getStructTreeRoot().getPageMarkedContentReferences(page.getKey());
            if (mcrs != null) {
                for (PdfMcr mcr : mcrs) {
                    if (mcr instanceof PdfMcrDictionary || mcr instanceof PdfObjRef) {
                        objectsToCopy.add(mcr.getPdfObject());
                    }
                    PdfDictionary top = addAllParentsToSet(mcr, objectsToCopy);
                    if (top.isFlushed()) {
                        throw new PdfException(PdfException.CannotCopyFlushedTag);
                    }
                    tops.add(top);
                }
            }
        }

        List<PdfDictionary> topsInOriginalOrder = new ArrayList<>();
        for (IPdfStructElem kid : fromDocument.getStructTreeRoot().getKids()) {
            if (kid == null)  continue;

            PdfDictionary kidObject = ((PdfStructElem) kid).getPdfObject();
            if (tops.contains(kidObject)) {
                topsInOriginalOrder.add(kidObject);
            }
        }
        for (PdfDictionary top : topsInOriginalOrder) {
            PdfDictionary copied = copyObject(top, objectsToCopy, destDocument, page2pageDictionaries, copyFromDestDocument);
            destDocument.getStructTreeRoot().addKidObject(insertIndex, copied);
            if (insertIndex > -1) {
                ++insertIndex;
            }
        }
    }

    private static PdfDictionary copyObject(PdfDictionary source, Set<PdfObject> objectsToCopy, PdfDocument toDocument, Map<PdfDictionary, PdfDictionary> page2page, boolean copyFromDestDocument) {
        PdfDictionary copied;
        if (copyFromDestDocument) {
            copied = source.clone(ignoreKeysForCopy);
            if (source.isIndirect()) {
                copied.makeIndirect(toDocument);
            }
        }
        else
            copied = source.copyTo(toDocument, ignoreKeysForCopy, true);

        if (source.containsKey(PdfName.Obj)) {
            PdfDictionary obj = source.getAsDictionary(PdfName.Obj);
            if (!copyFromDestDocument && obj != null) {
                // Link annotations could be not added to the toDocument, so we need to identify this case.
                // When obj.copyTo is called, and annotation was already copied, we would get this already created copy.
                // If it was already copied and added, /P key would be set. Otherwise /P won't be set.
                obj = obj.copyTo(toDocument, Arrays.asList(PdfName.P), false);
                copied.put(PdfName.Obj, obj);
            }
        }

        PdfDictionary pg = source.getAsDictionary(PdfName.Pg);
        if (pg != null) {
            //TODO It is possible, that pg will not be present in the page2page map. Consider the situation,
            // that we want to copy structElem because it has marked content dictionary reference, which belongs to the page from page2page,
            // but the structElem itself has /Pg which value could be arbitrary page.
            copied.put(PdfName.Pg, page2page.get(pg));
        }
        PdfObject k = source.get(PdfName.K);
        if (k != null) {
            if (k.isArray()) {
                PdfArray kArr = (PdfArray) k;
                PdfArray newArr = new PdfArray();
                for (int i = 0; i < kArr.size(); i++) {
                    PdfObject copiedKid = copyObjectKid(kArr.get(i), copied, objectsToCopy, toDocument, page2page, copyFromDestDocument);
                    if (copiedKid != null) {
                        newArr.add(copiedKid);
                    }
                }
                // TODO new array may be empty or with single element
                copied.put(PdfName.K, newArr);
            } else {
                PdfObject copiedKid = copyObjectKid(k, copied, objectsToCopy, toDocument, page2page, copyFromDestDocument);
                if (copiedKid != null) {
                    copied.put(PdfName.K, copiedKid);
                }
            }
        }
        return copied;
    }

    private static PdfObject copyObjectKid(PdfObject kid, PdfDictionary copiedParent, Set<PdfObject> objectsToCopy,
                                           PdfDocument toDocument, Map<PdfDictionary, PdfDictionary> page2page, boolean copyFromDestDocument) {
        if (kid.isNumber()) {
            toDocument.getStructTreeRoot().getParentTreeHandler()
                    .registerMcr(new PdfMcrNumber((PdfNumber) kid, new PdfStructElem(copiedParent)));
            return kid; // TODO do we always copy numbers? don't we need to check if it is supposed to be copied like objs in objectsToCopy?
        } else if (kid.isDictionary()) {
            PdfDictionary kidAsDict = (PdfDictionary) kid;
            if (objectsToCopy.contains(kidAsDict)) {
                boolean hasParent = kidAsDict.containsKey(PdfName.P);
                PdfDictionary copiedKid = copyObject(kidAsDict, objectsToCopy, toDocument, page2page, copyFromDestDocument);
                if (hasParent) {
                    copiedKid.put(PdfName.P, copiedParent);
                } else {
                    PdfMcr mcr;
                    if (copiedKid.containsKey(PdfName.Obj)) {
                        mcr = new PdfObjRef(copiedKid, new PdfStructElem(copiedParent));
                        PdfDictionary contentItemObject = copiedKid.getAsDictionary(PdfName.Obj);
                        if (PdfName.Link.equals(contentItemObject.getAsName(PdfName.Subtype))
                                && !contentItemObject.containsKey(PdfName.P)) {
                            // Some link annotations may be not copied, because their destination page is not copied.
                            return null;
                        }
                        contentItemObject.put(PdfName.StructParent, new PdfNumber((int) toDocument.getNextStructParentIndex()));
                    } else {
                        mcr = new PdfMcrDictionary(copiedKid, new PdfStructElem(copiedParent));
                    }
                    toDocument.getStructTreeRoot().getParentTreeHandler().registerMcr(mcr);
                }
                return copiedKid;
            }
        }
        return null;
    }

    private static void separateKids(PdfDictionary structElem, Set<PdfObject> firstPartElems, LastClonedAncestor lastCloned) {
        PdfObject k = structElem.get(PdfName.K);

        // If /K entry is not a PdfArray - it would be a kid which we won't clone at the moment, because it won't contain
        // kids from both parts at the same time. It would either be cloned as an ancestor later, or not cloned at all.
        // If it's kid is struct elem - it would definitely be structElem from the first part, so we simply call separateKids for it.
        if (!k.isArray()) {
            if (k.isDictionary() && PdfStructElem.isStructElem((PdfDictionary) k)) {
                separateKids((PdfDictionary) k, firstPartElems, lastCloned);
            }
        } else {
            PdfDocument document = structElem.getIndirectReference().getDocument();

            PdfArray kids = (PdfArray) k;

            for (int i = 0; i < kids.size(); ++i) {
                PdfObject kid = kids.get(i);
                PdfDictionary dictKid = null;
                if (kid.isDictionary()) {
                    dictKid = (PdfDictionary) kid;
                }

                if (dictKid != null && PdfStructElem.isStructElem(dictKid)) {
                    if (firstPartElems.contains(kid)) {
                        separateKids((PdfDictionary) kid, firstPartElems, lastCloned);
                    } else {
                        if (dictKid.isFlushed()) {
                            throw new PdfException(PdfException.TagFromTheExistingTagStructureIsFlushedCannotAddCopiedPageTags);
                        }

                        // elems with no kids will not be marked as from the first part,
                        // but nonetheless we don't want to move all of them to the second part; we just leave them as is
                        if (dictKid.containsKey(PdfName.K)) {
                            cloneParents(structElem, lastCloned, document);

                            kids.remove(i--);
                            PdfStructElem.addKidObject(lastCloned.clone, -1, kid);
                        }
                    }
                } else {
                    if (!firstPartElems.contains(kid)) {
                        cloneParents(structElem, lastCloned, document);

                        PdfMcr mcr;
                        if (dictKid != null) {
                            if (dictKid.get(PdfName.Type).equals(PdfName.MCR)) {
                                mcr = new PdfMcrDictionary(dictKid, new PdfStructElem(lastCloned.clone));
                            } else {
                                mcr = new PdfObjRef(dictKid, new PdfStructElem(lastCloned.clone));
                            }
                        } else {
                            mcr = new PdfMcrNumber((PdfNumber) kid, new PdfStructElem(lastCloned.clone));
                        }

                        kids.remove(i--);
                        PdfStructElem.addKidObject(lastCloned.clone, -1, kid);
                        document.getStructTreeRoot().getParentTreeHandler().registerMcr(mcr); // re-register mcr
                    }
                }
            }
        }

        if (lastCloned.ancestor == structElem) {
            lastCloned.ancestor = lastCloned.ancestor.getAsDictionary(PdfName.P);
            lastCloned.clone = lastCloned.clone.getAsDictionary(PdfName.P);
        }
    }

    private static void cloneParents(PdfDictionary structElem, LastClonedAncestor lastCloned, PdfDocument document) {
        if (lastCloned.ancestor != structElem) {
            PdfDictionary structElemClone = structElem.clone(ignoreKeysForClone).makeIndirect(document);
            PdfDictionary currClone = structElemClone;
            PdfDictionary currElem = structElem;
            while (currElem.get(PdfName.P) != lastCloned.ancestor) {
                PdfDictionary parent = currElem.getAsDictionary(PdfName.P);
                PdfDictionary parentClone = parent.clone(ignoreKeysForClone).makeIndirect(document);
                currClone.put(PdfName.P, parentClone);
                parentClone.put(PdfName.K, currClone);
                currClone = parentClone;
                currElem = parent;
            }
            PdfStructElem.addKidObject(lastCloned.clone, -1, currClone);
            lastCloned.clone = structElemClone;
            lastCloned.ancestor = structElem;
        }
    }

    /**
     * @return the topmost parent added to set. If encountered flushed element - stops and returns this flushed element.
     */
    private static PdfDictionary addAllParentsToSet(PdfMcr mcr, Set<PdfObject> set) {
        PdfDictionary elem = ((PdfStructElem) mcr.getParent()).getPdfObject();
        set.add(elem);
        for (; ; ) {
            if (elem.isFlushed()) { break; }
            PdfDictionary p = elem.getAsDictionary(PdfName.P);
            if (p == null || PdfName.StructTreeRoot.equals(p.getAsName(PdfName.Type))) {
                break;
            } else {
                elem = p;
                set.add(elem);
            }
        }
        return elem;
    }

    static class LastClonedAncestor {
        PdfDictionary ancestor;
        PdfDictionary clone;
    }
}
