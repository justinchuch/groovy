/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.codehaus.groovy.control;

import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.Verifier;

import java.math.BigDecimal;

import static org.apache.groovy.ast.tools.ExpressionUtils.transformInlineConstants;
import static org.codehaus.groovy.ast.ClassHelper.isBigDecimalType;
import static org.codehaus.groovy.ast.ClassHelper.isStringType;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperByte;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperCharacter;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperDouble;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperFloat;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperInteger;
import static org.codehaus.groovy.ast.ClassHelper.isWrapperShort;

/**
 * Resolves constants in annotation definitions.
 */
public class AnnotationConstantsVisitor extends ClassCodeVisitorSupport {

    private boolean annotationDef;
    private SourceUnit sourceUnit;

    @Override
    protected SourceUnit getSourceUnit() {
        return sourceUnit;
    }

    public void visitClass(final ClassNode classNode, final SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
        this.annotationDef = classNode.isAnnotationDefinition();
        super.visitClass(classNode);
        this.annotationDef = false;
    }

    @Override
    protected void visitConstructorOrMethod(final MethodNode node, final boolean isConstructor) {
        if (annotationDef) {
            Statement statement = node.getFirstStatement();
            if (statement instanceof ReturnStatement) {
                ReturnStatement rs = (ReturnStatement) statement;
                rs.setExpression(transformConstantExpression(rs.getExpression(), node.getReturnType()));
            } else if (statement instanceof ExpressionStatement) {
                ExpressionStatement es = (ExpressionStatement) statement;
                es.setExpression(transformConstantExpression(es.getExpression(), node.getReturnType()));
            }
        }
    }

    private static Expression transformConstantExpression(Expression val, ClassNode returnType) {
        ClassNode returnWrapperType = ClassHelper.getWrapper(returnType);
        if (val instanceof ConstantExpression) {
            Expression result = revertType(val, returnWrapperType);
            if (result != null) {
                return result;
            }
            return val;
        }
        if (val instanceof CastExpression) {
            CastExpression castExp = (CastExpression) val;
            Expression castee = castExp.getExpression();
            if (castee instanceof ConstantExpression) {
                if (ClassHelper.getWrapper(castee.getType()).isDerivedFrom(returnWrapperType)) {
                    return castee;
                }
                Expression result = revertType(castee, returnWrapperType);
                if (result != null) {
                    return result;
                }
                return castee;
            }
        }
        return transformInlineConstants(val, returnType);
    }

    private static Expression revertType(Expression val, ClassNode returnWrapperType) {
        ConstantExpression ce = (ConstantExpression) val;
        if (isWrapperCharacter(returnWrapperType) && isStringType(val.getType())) {
            return configure(val, Verifier.transformToPrimitiveConstantIfPossible((ConstantExpression) val));
        }
        ClassNode valWrapperType = ClassHelper.getWrapper(val.getType());
        if (isWrapperInteger(valWrapperType)) {
            Integer i = (Integer) ce.getValue();
            if (isWrapperCharacter(returnWrapperType)) {
                return configure(val, new ConstantExpression((char) i.intValue(), true));
            }
            if (isWrapperShort(returnWrapperType)) {
                return configure(val, new ConstantExpression(i.shortValue(), true));
            }
            if (isWrapperByte(returnWrapperType)) {
                return configure(val, new ConstantExpression(i.byteValue(), true));
            }
        }
        if (isBigDecimalType(valWrapperType)) {
            BigDecimal bd = (BigDecimal) ce.getValue();
            if (isWrapperFloat(returnWrapperType)) {
                return configure(val, new ConstantExpression(bd.floatValue(), true));
            }
            if (isWrapperDouble(returnWrapperType)) {
                return configure(val, new ConstantExpression(bd.doubleValue(), true));
            }
        }
        return null;
    }

    private static Expression configure(Expression orig, Expression result) {
        result.setSourcePosition(orig);
        return result;
    }
}
