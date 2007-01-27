/* 
 * Spoon - http://spoon.gforge.inria.fr/
 * Copyright (C) 2006 INRIA Futurs <renaud.pawlak@inria.fr>
 * 
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify 
 * and/or redistribute the software under the terms of the CeCILL-C license as 
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info. 
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *  
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */

package spoon.support.reflect.eval;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtAssert;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtCodeSnippetExpression;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtContinue;
import spoon.reflect.code.CtDo;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtOperatorAssignment;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.code.CtSynchronized;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.code.CtWhile;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtAnnotationType;
import spoon.reflect.declaration.CtAnonymousExecutable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtSimpleType;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.eval.StepKind;
import spoon.reflect.eval.SymbolicEvaluationPath;
import spoon.reflect.eval.SymbolicEvaluationStack;
import spoon.reflect.eval.SymbolicEvaluator;
import spoon.reflect.eval.SymbolicHeap;
import spoon.reflect.eval.SymbolicInstance;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtGenericElementReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtParameterReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.CtVisitor;
import spoon.reflect.visitor.Query;
import spoon.support.query.TypeFilter;

/**
 * This visitor implements an abstract evaluator for the program compile-time
 * metamodel.
 */
public class VisitorSymbolicEvaluator implements CtVisitor, SymbolicEvaluator {

	List<CtTypeReference> statefullExternals = new ArrayList<CtTypeReference>();

	public List<CtTypeReference> getStatefullExternals() {
		return statefullExternals;
	}

	public VisitorSymbolicEvaluator() {
	}

	List<SymbolicEvaluationPath> paths = new ArrayList<SymbolicEvaluationPath>();

	private void startPath() {
		paths.add(new SymbolicEvaluationPath());
	}

	private SymbolicEvaluationPath getCurrentPath() {
		return paths.get(paths.size() - 1);
	}

	// private void addToPath(AbstractStackFrame frame) {
	// paths.get(paths.size() - 1).add(frame);
	// }

	public List<SymbolicEvaluationPath> getPaths() {
		return paths;
	}

	public void dumpPaths() {
		int i = 1;
		for (SymbolicEvaluationPath p : paths) {
			System.out.println("-- path " + (i++));
			p.dump();
		}
	}

	private void resetCurrentEvaluation() {
		stack = new SymbolicEvaluationStack();
		heap.clear();
		SymbolicInstance.resetIds();
		result = null;
	}

	public void reset() {
		resetCurrentEvaluation();
		branchingPoints.clear();
		paths.clear();
	}

	private SymbolicInstance result = null;

	void enterExecutable(CtAbstractInvocation<?> caller,
			CtExecutableReference<?> eref, SymbolicInstance target,
			List<SymbolicInstance> args) {
		Map<CtVariableReference, SymbolicInstance> variables = new HashMap<CtVariableReference, SymbolicInstance>();
		CtExecutable<?> e = eref.getDeclaration();
		if (e != null) {
			// initialize arguments
			int i = 0;
			for (CtVariable<?> v : e.getParameters()) {
				variables.put(v.getReference(), args.get(i++));
			}
			// initialize local variables
			for (CtVariable<?> v : Query.getElements(e.getBody(),
					new TypeFilter<CtVariable>(CtVariable.class))) {
				variables.put(v.getReference(), null);
			}
		}
		stack.enterFrame(caller, target, eref, args, variables);
		getCurrentPath().addStep(StepKind.ENTER, this);
	}

	void exitExecutable(CtExecutableReference<?> eref) {
		stack.setResult(result);
		getCurrentPath().addStep(StepKind.EXIT, this);
		stack.exitFrame();
	}

	protected Stack<BranchingPoint> branchingPoints = new Stack<BranchingPoint>();

	protected SymbolicEvaluationStack stack = new SymbolicEvaluationStack();

	protected SymbolicHeap heap = new SymbolicHeap();

	Number convert(CtTypeReference<?> type, Number n) {
		if (type.getActualClass() == int.class
				|| type.getActualClass() == Integer.class) {
			return n.intValue();
		}
		if (type.getActualClass() == byte.class
				|| type.getActualClass() == Byte.class) {
			return n.byteValue();
		}
		if (type.getActualClass() == long.class
				|| type.getActualClass() == Long.class) {
			return n.longValue();
		}
		if (type.getActualClass() == float.class
				|| type.getActualClass() == Float.class) {
			return n.floatValue();
		}
		if (type.getActualClass() == short.class
				|| type.getActualClass() == Short.class) {
			return n.shortValue();
		}
		return n;
	}

	class BranchingPoint {
		public BranchingPoint(SymbolicEvaluationStack stack,
				CtElement... branches) {
			this.stack = new SymbolicEvaluationStack(stack);
			this.branches = Arrays.asList(branches);
			this.uncompletedBranches = new ArrayList<CtElement>(this.branches);
		}

		public List<CtElement> branches;

		public SymbolicEvaluationStack stack;

		public List<CtElement> uncompletedBranches;

		public List<CtElement> completedBranches = new ArrayList<CtElement>();

		public SymbolicInstance evaluate(VisitorSymbolicEvaluator evaluator) {
			return evaluator.evaluate(uncompletedBranches.get(0));
		}

		public boolean nextBranch() {
			completedBranches.add(uncompletedBranches.get(0));
			uncompletedBranches.remove(0);
			if (uncompletedBranches.isEmpty()) {
				return false;
			} else {
				return true;
			}
		}

		@Override
		public String toString() {
			return branches.toString();
		}
	}

	private BranchingPoint getBranchingPoint(CtElement... branches) {
		if (!branchingPoints.isEmpty()) {
			// look for the first uncompleted bp at the top of the stack
			boolean first = true;
			do {
				BranchingPoint bp = branchingPoints.peek();
				if (bp.stack.equals(stack)
						&& bp.branches.equals(Arrays.asList(branches))) {
					bp.nextBranch();
					return bp;
					// if (!bp.nextBranch()) {
					// branchingPoints.pop();
					// } else {
					// return bp;
					// }
				} else {
					first = false;
				}
			} while (!branchingPoints.isEmpty() && first);
			// look for any bp in the stack
			for (int i = branchingPoints.size() - 2; i >= 0; i--) {
				BranchingPoint bp = branchingPoints.get(i);
				if (bp.stack.equals(stack)
						&& bp.branches.equals(Arrays.asList(branches))) {
					return bp;
				}
			}
		}
		// create a new branch
		BranchingPoint bp = new BranchingPoint(stack, branches);
		branchingPoints.push(bp);
		return bp;
	}

	@SuppressWarnings("unchecked")
	protected SymbolicInstance evaluateBranches(CtElement... branches) {
		// System.out.println("branches: "+Arrays.asList(branches));
		BranchingPoint bp = getBranchingPoint(branches);
		// System.out.println("bp: "+bp);
		result = bp.evaluate(this);
		// System.out.println("result: "+result);
		// remove completed bp
		if (branchingPoints.peek() == bp) {
			if (bp.uncompletedBranches.size() == 1) {
				branchingPoints.pop();
			}
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public SymbolicInstance evaluate(CtElement element) {
		if (element == null)
			return null;
		// System.out.println("[evaluating
		// "+element.getClass().getSimpleName()+"]");
		element.accept(this);
		return result;
	}

	// private boolean evaluationCompleted() {
	// return branchingPoints.size() == 1
	// && branchingPoints.peek().uncompletedBranches.size() == 1;
	// }

	@SuppressWarnings("unchecked")
	public void invoke(CtExecutable executable, SymbolicInstance... args) {
		do {
			resetCurrentEvaluation();
			// AbstractInstance.dumpHeap();
			startPath();
			List<SymbolicInstance> cargs = new ArrayList<SymbolicInstance>();
			for (SymbolicInstance i : args) {
				cargs.add(i == null ? null : i.getClone());
			}
			SymbolicInstance target = heap.getType(this, executable
					.getDeclaringType().getReference());
			try {
				invoke(null, executable.getReference(), target, cargs);
			} catch (SymbolicWrappedException e) {
				// swallow it
			}
			// System.out.println("END");
			// stack.dumpFrameStack();
			// heap.dump();
		} while (!branchingPoints.isEmpty());
		// dumpPaths();
	}

	@SuppressWarnings("unchecked")
	public void invoke(SymbolicInstance target, CtExecutable executable,
			List<SymbolicInstance> args) {
		do {
			resetCurrentEvaluation();
			// AbstractInstance.dumpHeap();
			startPath();
			List<SymbolicInstance> cargs = null;
			if (args != null) {
				cargs = new ArrayList<SymbolicInstance>();
				for (SymbolicInstance i : args) {
					cargs.add(i == null ? null : i.getClone());
				}
			}
			try {
				invoke(null, executable.getReference(), target, cargs);
			} catch (SymbolicWrappedException e) {
				e.printStackTrace();
				// swallow it
			}
			// System.out.println("END");
			// stack.dumpFrameStack();
			// heap.dump();
		} while (!branchingPoints.isEmpty());
		// dumpPaths();
	}

	/**
	 * Tell if the given method follows the getter naming conventions.
	 */
	boolean isGetter(CtExecutableReference e) {
		return e.getSimpleName().startsWith("get")
				&& e.getParameterTypes().size() == 0;
	}

	/**
	 * Tell if the given method follows the setter naming conventions.
	 */
	boolean isSetter(CtExecutableReference e) {
		return e.getSimpleName().startsWith("set")
				&& e.getParameterTypes().size() == 1;
	}

	boolean isStateFullExternal(CtTypeReference type) {
		for (CtTypeReference<?> t : getStatefullExternals()) {
			if (t.isAssignableFrom(type)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	private <T> SymbolicInstance<T> invoke(CtAbstractInvocation<?> caller,
			CtExecutableReference<T> executable, SymbolicInstance target,
			List<SymbolicInstance> args) {
		enterExecutable(caller, executable, target, args);
		// System.out.println("[invoking " + caller + "]");
		// stack.dump();
		// heap.dump();
		try {
			CtExecutable<?> decl = executable.getDeclaration();
			if (decl != null) {
				if (decl.getBody() != null) {
					evaluate(decl.getBody());
				} else {
					result = new SymbolicInstance(this, executable.getType(),
							false);
				}
			} else {
				// not accessible (set the result to the return type or to the
				// field value if a getter)
				CtFieldReference fref = null;
				if (isStateFullExternal(executable.getDeclaringType())) {
					if (target != null && isGetter(executable)) {
						// System.out.println(m);
						SymbolicInstance r = null;
						fref = executable.getFactory().Field().createReference(
								target.getConcreteType(), executable.getType(),
								executable.getSimpleName().substring(3));
						r = heap.get(target.getFieldValue(fref));
						if (r != null) {
							result = r;
						} else {
							result = new SymbolicInstance(this, executable
									.getType(), false);
						}
					} else if (target != null && isSetter(executable)) {
						// System.out.println(m.toString()+"
						// "+caller.getPosition());
						fref = executable.getFactory().Field().createReference(
								target.getConcreteType(), executable.getType(),
								executable.getSimpleName().substring(3));
						target.setFieldValue(heap, fref, args.get(0));
						result = new SymbolicInstance(this, executable
								.getType(), false);
						// heap.dump();
						// stack.dump();
					} else {
						result = new SymbolicInstance(this, executable
								.getType(), false);
					}
				} else {
					result = new SymbolicInstance(this, executable.getType(),
							false);
				}
			}
		} catch (ReturnException e) {
			// normal return
		} finally {
			exitExecutable(executable);
		}
		return result;
	}

	public <A extends Annotation> void visitCtAnnotation(
			CtAnnotation<A> annotation) {
		throw new RuntimeException("Unknow Element");
	}

	public <A extends Annotation> void visitCtAnnotationType(
			CtAnnotationType<A> annotationType) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtAnonymousExecutable(CtAnonymousExecutable impl) {
		throw new RuntimeException("Unknow Element");
	}

	public <T, E extends CtExpression<?>> void visitCtArrayAccess(
			CtArrayAccess<T, E> arrayAccess) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtArrayTypeReference(CtArrayTypeReference<T> reference) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtCodeSnippetExpression(
			CtCodeSnippetExpression<T> expression) {
	}

	public void visitCtCodeSnippetStatement(CtCodeSnippetStatement statement) {
	}

	public void visitCtAssert(CtAssert asserted) {
		throw new RuntimeException("Unknow Element");
	}

	public <T, A extends T> void visitCtAssignment(CtAssignment<T, A> assignment) {
		if (assignment.getAssigned() instanceof CtVariableAccess) {
			CtVariableReference<T> vref = ((CtVariableAccess<T>) assignment
					.getAssigned()).getVariable();
			SymbolicInstance res = evaluate(assignment.getAssignment());
			if (vref instanceof CtFieldReference) {
				stack.getThis().setFieldValue(heap, vref, res);
			} else {
				stack.setVariableValue(vref, res);
			}
			result = res;
		}
	}

	@SuppressWarnings("unchecked")
	public <T> void visitCtBinaryOperator(CtBinaryOperator<T> operator) {
		SymbolicInstance left = evaluate(operator.getLeftHandOperand());
		SymbolicInstance right = evaluate(operator.getRightHandOperand());
		switch (operator.getKind()) {
		case AND:
		case OR:
		case EQ:
		case NE:
		case GE:
		case LE:
		case GT:
		case LT:
		case INSTANCEOF:
			SymbolicInstance<Boolean> bool = new SymbolicInstance<Boolean>(
					this, operator.getFactory().Type().createReference(
							boolean.class), false);
			result = bool;
			return;
		case MINUS:
		case MUL:
		case DIV:
			SymbolicInstance<Number> number = new SymbolicInstance<Number>(
					this, operator.getFactory().Type().createReference(
							Number.class), false);
			result = number;
			return;
		case PLUS:
			if ((left.getConcreteType().getActualClass() == String.class)
					|| (right.getConcreteType().getActualClass() == String.class)) {
				SymbolicInstance<String> string = new SymbolicInstance<String>(
						this, operator.getFactory().Type().createReference(
								String.class), false);
				result = string;
				return;
			} else {
				bool = new SymbolicInstance<Boolean>(this, operator
						.getFactory().Type().createReference(boolean.class),
						false);
				result = bool;
				return;
			}
		default:
			throw new RuntimeException("unsupported operator");
		}
	}

	public <R> void visitCtBlock(CtBlock<R> block) {
		for (CtStatement s : block.getStatements()) {
			evaluate(s);
		}
	}

	public void visitCtBreak(CtBreak breakStatement) {
		throw new RuntimeException("Unknow Element");
	}

	public <E> void visitCtCase(CtCase<E> caseStatement) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtCatch(CtCatch catchBlock) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtClass(CtClass<T> ctClass) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtConstructor(CtConstructor c) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtContinue(CtContinue continueStatement) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtDo(CtDo doLoop) {
		evaluate(doLoop.getBody());
		evaluate(doLoop.getLoopingExpression());
	}

	public <T extends Enum> void visitCtEnum(CtEnum<T> ctEnum) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtExecutableReference(
			CtExecutableReference<T> reference) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtExpression(CtExpression<?> expression) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtField(CtField<T> f) {
		throw new RuntimeException("Unknow Element");
	}

	boolean isAccessible(CtFieldReference<?> field) {
		return field.getDeclaringType().isAssignableFrom(
				stack.getThis().getConcreteType());
	}

	public <T> void visitCtFieldAccess(CtFieldAccess<T> fieldAccess) {
		if (fieldAccess.getVariable().getSimpleName().equals("this")) {
			result = stack.getThis();
			return;
		}
		if (fieldAccess.getVariable().getSimpleName().equals("class")) {
			SymbolicInstance type = heap.getType(this, fieldAccess.getType());
			result = type;
			return;
		}
		SymbolicInstance<?> target = evaluate(fieldAccess.getTarget());
		if (target == null) {
			if (isAccessible(fieldAccess.getVariable())) {
				target = stack.getThis();
			}
		}
		if (target != null && !target.isExternal()) {
			result = heap.get(target.getFieldValue(fieldAccess.getVariable()));
		} else {
			// set the type to the declared one
			SymbolicInstance<T> i = new SymbolicInstance<T>(this, fieldAccess
					.getType(), false);
			// this instance is not put on the heap because it will be put if
			// assigned to an object's field
			result = i;
		}
	}

	public <T> void visitCtFieldReference(CtFieldReference<T> reference) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtFor(CtFor forLoop) {
		for (CtStatement s : forLoop.getForInit()) {
			evaluate(s);
		}
		evaluate(forLoop.getExpression());
		evaluate(forLoop.getBody());
		for (CtStatement s : forLoop.getForUpdate()) {
			evaluate(s);
		}
	}

	public void visitCtForEach(CtForEach foreach) {
		evaluate(foreach.getBody());
	}

	public void visitCtGenericElementReference(
			CtGenericElementReference reference) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtIf(CtIf ifElement) {
		evaluate(ifElement.getCondition());
		evaluateBranches(ifElement.getThenStatement(), ifElement
				.getElseStatement());
	}

	public <T> void visitCtInterface(CtInterface<T> intrface) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtInvocation(CtInvocation<T> invocation) {
		CtExecutableReference<T> eref = invocation.getExecutable();
//		if (eref.getSimpleName().equals("<init>"))
//			return;
		List<SymbolicInstance> arguments = new ArrayList<SymbolicInstance>();
		for (CtExpression expr : invocation.getArguments()) {
			SymbolicInstance o = evaluate(expr);
			arguments.add(o);
		}
		SymbolicInstance<?> target = evaluate(invocation.getTarget());
		// redirect ref to the overloading method if any
		if (target != null) {
			CtExecutableReference<T> override = eref
					.getOverridingExecutable(target.getConcreteType());
			if (override != null) {
				eref = override;
			}
		}
		if (target == null) {
			if (eref.isStatic()) {
				target = heap.getType(this, eref.getDeclaringType());
			} else {
				target = stack.getThis();
			}
		}
		// CtExecutable<T> e = eref.getDeclaration();
		// if (e != null) {
		invoke(invocation, eref, (SymbolicInstance) target, arguments);
		// } else {
		// // method is not accessible
		// // set the result to the declared type
		// Method m = invocation.getExecutable().getActualMethod();
		// stack.setResult(heap.get(this, invocation.getFactory().Type()
		// .createReference(m.getReturnType())));
		// }
	}

	public <T> void visitCtLiteral(CtLiteral<T> literal) {
		result = new SymbolicInstance<T>(this, literal.getType(), false);
	}

	public <T> void visitCtLocalVariable(final CtLocalVariable<T> localVariable) {
		stack.setVariableValue(localVariable.getReference(),
				evaluate(localVariable.getDefaultExpression()));
	}

	public <T> void visitCtLocalVariableReference(
			CtLocalVariableReference<T> reference) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtMethod(CtMethod<T> m) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtNewArray(CtNewArray<T> newArray) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtNewClass(CtNewClass<T> newClass) {
		CtSimpleType<T> c = newClass.getType().getDeclaration();
		// CtExecutable<T> e = eref.getDeclaration();
		List<SymbolicInstance> arguments = new ArrayList<SymbolicInstance>();
		for (CtExpression expr : newClass.getArguments()) {
			SymbolicInstance o = evaluate(expr);
			arguments.add(o);
		}
		SymbolicInstance<T> i = new SymbolicInstance<T>(this, newClass
				.getType(), false);
		heap.store(i);
		if (c != null) {
			// evaluate the constructor
			invoke(newClass, newClass.getExecutable(), i, arguments);
		}
		// TODO: something better for externals
		result = i;
	}

	public <T, A extends T> void visitCtOperatorAssignement(
			CtOperatorAssignment<T, A> assignment) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtPackage(CtPackage ctPackage) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtPackageReference(CtPackageReference reference) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtParameter(CtParameter<T> parameter) {
		throw new RuntimeException("Unknow Element");
	}

	public <R> void visitCtStatementList(CtStatementList<R> statements) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtParameterReference(CtParameterReference<T> reference) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtReference(CtReference reference) {
		throw new RuntimeException("Unknow Element");
	}

	public <R> void visitCtReturn(CtReturn<R> returnStatement) {
		result = evaluate(returnStatement.getReturnedExpression());
		throw new ReturnException(returnStatement);
	}

	public <E> void visitCtSwitch(CtSwitch<E> switchStatement) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtSynchronized(CtSynchronized synchro) {
		throw new RuntimeException("Unknow Element");
	}

	public <T, E extends CtExpression<?>> void visitCtTargetedExpression(
			CtTargetedExpression<T, E> targetedExpression) {
		throw new RuntimeException("Unknow Element");
	}

	@SuppressWarnings("unchecked")
	public void visitCtThrow(CtThrow throwStatement) {
		throw new SymbolicWrappedException(evaluate(throwStatement
				.getThrownExpression()), throwStatement, getStack());
	}

	public void visitCtTry(CtTry tryBlock) {
		try {
			evaluate(tryBlock.getBody());
		} catch (ReturnException r) {
			// normal return
		} catch (SymbolicWrappedException e) {
			for (CtCatch c : tryBlock.getCatchers()) {
				if (c.getParameter().getType().isAssignableFrom(
						e.getAbstractCause().getConcreteType())) {
					getStack().setVariableValue(
							c.getParameter().getReference(),
							e.getAbstractCause());
					evaluate(c.getBody());
					return;
				}
			}
			// re-throw unhandled exception
			throw e;
		} finally {
			evaluate(tryBlock.getFinalizer());
		}
	}

	public void visitCtTypeParameter(CtTypeParameter typeParameter) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtTypeParameterReference(CtTypeParameterReference ref) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
		throw new RuntimeException("Unknow Element");
	}

	public <T> void visitCtUnaryOperator(CtUnaryOperator<T> operator) {
		evaluate(operator.getOperand());
		// switch (operator.getKind()) {
		// case POSTINC:
		// case POSTDEC:
		// case PREINC:
		// case PREDEC:
		// case NEG:
		// AbstractInstance<Number>
		// number=AbstractInstance.get(this,operator.getFactory().Type().createReference(Number.class));
		// setResult(number);
		// return;
		// case NOT:
		// AbstractInstance<Boolean>
		// bool=AbstractInstance.get(this,operator.getFactory().Type().createReference(Boolean.class));
		// setResult(bool);
		// return;
		// default:
		// throw new RuntimeException("unsupported operator");
		// }
	}

	public <T> void visitCtVariableAccess(CtVariableAccess<T> variableAccess) {
		CtVariableReference<?> vref = variableAccess.getVariable();
		result = stack.getVariableValue(vref);
	}

	public void visitCtVariableReference(CtVariableReference reference) {
		throw new RuntimeException("Unknow Element");
	}

	public void visitCtWhile(CtWhile whileLoop) {
		evaluate(whileLoop.getLoopingExpression());
		evaluate(whileLoop.getBody());
	}

	public <T> void visitCtConditional(CtConditional<T> conditional) {
		evaluate(conditional.getCondition());
		evaluate(conditional.getThenExpression());
		evaluate(conditional.getElseExpression());
	}

	public SymbolicHeap getHeap() {
		return heap;
	}

	public SymbolicEvaluationStack getStack() {
		return stack;
	}

}