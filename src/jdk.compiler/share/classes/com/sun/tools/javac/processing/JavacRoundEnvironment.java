/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.processing;

import java.lang.annotation.Annotation;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.util.*;
import java.util.*;

import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.DefinedBy.Api;

/**
 * Object providing state about a prior round of annotation processing.
 *
 * <p>The methods in this class do not take type annotations into account,
 * as target types, not java elements.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class JavacRoundEnvironment implements RoundEnvironment {
    // Default equals and hashCode methods are okay.

    private final boolean processingOver;
    private final boolean errorRaised;
    private final ProcessingEnvironment processingEnv;
    private final Elements eltUtils;

    // Caller must pass in an immutable set
    private final Set<? extends Element> rootElements;

    JavacRoundEnvironment(boolean processingOver,
                          boolean errorRaised,
                          Set<? extends Element> rootElements,
                          ProcessingEnvironment processingEnv) {
        this.processingOver = processingOver;
        this.errorRaised = errorRaised;
        this.rootElements = rootElements;
        this.processingEnv = processingEnv;
        this.eltUtils = processingEnv.getElementUtils();
    }

    public String toString() {
        return String.format("[errorRaised=%b, rootElements=%s, processingOver=%b]",
                             errorRaised,
                             rootElements,
                             processingOver);
    }

    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public boolean processingOver() {
        return processingOver;
    }

    /**
     * Returns {@code true} if an error was raised in the prior round
     * of processing; returns {@code false} otherwise.
     *
     * @return {@code true} if an error was raised in the prior round
     * of processing; returns {@code false} otherwise.
     */
    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public boolean errorRaised() {
        return errorRaised;
    }

    /**
     * Returns the type elements specified by the prior round.
     *
     * @return the types elements specified by the prior round, or an
     * empty set if there were none
     */
    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public Set<? extends Element> getRootElements() {
        return rootElements;
    }

    /**
     * Returns the elements annotated with the given annotation type.
     * Only type elements <i>included</i> in this round of annotation
     * processing, or declarations of members, parameters, or type
     * parameters declared within those, are returned.  Included type
     * elements are {@linkplain #getRootElements specified
     * types} and any types nested within them.
     *
     * @param a  annotation type being requested
     * @return the elements annotated with the given annotation type,
     * or an empty set if there are none
     */
    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public Set<? extends Element> getElementsAnnotatedWith(TypeElement a) {
        throwIfNotAnnotation(a);

        Set<Element> result = Collections.emptySet();
        ElementScanner9<Set<Element>, TypeElement> scanner =
            new AnnotationSetScanner(result);

        for (Element element : rootElements)
            result = scanner.scan(element, a);

        return result;
    }

    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public Set<? extends Element> getElementsAnnotatedWithAny(TypeElement... annotations) {
        // Don't bother to special-case annotations.length == 1 as
        // return getElementsAnnotatedWith(annotations[0]);

        Set<TypeElement> annotationSet = new LinkedHashSet<>(annotations.length);
        for (TypeElement annotation : annotations) {
            throwIfNotAnnotation(annotation);
            annotationSet.add(annotation);
        }

        Set<Element> result = Collections.emptySet();
        ElementScanner9<Set<Element>, Set<TypeElement>> scanner =
            new AnnotationSetMultiScanner(result);

        for (Element element : rootElements)
            result = scanner.scan(element, annotationSet);

        return result;
    }

    // Could be written as a local class inside getElementsAnnotatedWith
    private class AnnotationSetScanner extends
        ElementScanningIncludingTypeParameters<Set<Element>, TypeElement> {
        // Insertion-order preserving set
        private Set<Element> annotatedElements = new LinkedHashSet<>();

        AnnotationSetScanner(Set<Element> defaultSet) {
            super(defaultSet);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Set<Element> scan(Element e, TypeElement annotation) {
            for (AnnotationMirror annotMirror :  eltUtils.getAllAnnotationMirrors(e)) {
                if (annotation.equals(mirrorAsElement(annotMirror))) {
                    annotatedElements.add(e);
                    break;
                }
            }
            e.accept(this, annotation);
            return annotatedElements;
        }
    }

    // Could be written as a local class inside getElementsAnnotatedWithAny
    private class AnnotationSetMultiScanner extends
        ElementScanningIncludingTypeParameters<Set<Element>, Set<TypeElement>> {
        // Insertion-order preserving set
        private Set<Element> annotatedElements = new LinkedHashSet<>();

        AnnotationSetMultiScanner(Set<Element> defaultSet) {
            super(defaultSet);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public Set<Element> scan(Element e, Set<TypeElement> annotations) {
            for (AnnotationMirror annotMirror : eltUtils.getAllAnnotationMirrors(e)) {
                if (annotations.contains(mirrorAsElement(annotMirror))) {
                    annotatedElements.add(e);
                    break;
                }
            }
            e.accept(this, annotations);
            return annotatedElements;
        }
    }

    private static abstract class ElementScanningIncludingTypeParameters<R, P>
        extends ElementScanner9<R, P> {

        protected ElementScanningIncludingTypeParameters(R defaultValue) {
            super(defaultValue);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public R visitType(TypeElement e, P p) {
            // Type parameters are not considered to be enclosed by a type
            scan(e.getTypeParameters(), p);
            return super.visitType(e, p);
        }

        @Override @DefinedBy(Api.LANGUAGE_MODEL)
        public R visitExecutable(ExecutableElement e, P p) {
            // Type parameters are not considered to be enclosed by an executable
            scan(e.getTypeParameters(), p);
            return super.visitExecutable(e, p);
        }
    }

    /**
     * {@inheritDoc}
     */
    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> a) {
        throwIfNotAnnotation(a);
        String name = a.getCanonicalName();
        if (name == null)
            return Collections.emptySet();
        else {
            TypeElement annotationType = eltUtils.getTypeElement(name);
            if (annotationType == null)
                return Collections.emptySet();
            else
                return getElementsAnnotatedWith(annotationType);
        }
    }

    @DefinedBy(Api.ANNOTATION_PROCESSING)
    public Set<? extends Element> getElementsAnnotatedWithAny(Set<Class<? extends Annotation>> annotations) {
        List<TypeElement> annotationsAsElements = new ArrayList<>(annotations.size());

        for (Class<? extends Annotation> annotation : annotations) {
            throwIfNotAnnotation(annotation);
            String name = annotation.getCanonicalName();
            if (name == null)
                continue;
            annotationsAsElements.add(eltUtils.getTypeElement(name));
        }

        return getElementsAnnotatedWithAny(annotationsAsElements.toArray(new TypeElement[0]));
    }

    private Element mirrorAsElement(AnnotationMirror annotationMirror) {
        return annotationMirror.getAnnotationType().asElement();
    }

    private static final String NOT_AN_ANNOTATION_TYPE =
        "The argument does not represent an annotation type: ";

    private void throwIfNotAnnotation(Class<? extends Annotation> a) {
        if (!a.isAnnotation())
            throw new IllegalArgumentException(NOT_AN_ANNOTATION_TYPE + a);
    }

    private void throwIfNotAnnotation(TypeElement a) {
        if (a.getKind() != ElementKind.ANNOTATION_TYPE)
            throw new IllegalArgumentException(NOT_AN_ANNOTATION_TYPE + a);
    }
}
