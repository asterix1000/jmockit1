/*
 * Copyright (c) 2006 JMockit developers
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import java.util.*;
import javax.annotation.*;

import mockit.internal.expectations.*;

/**
 * Used to <em>record</em> expectations on {@linkplain Mocked mocked} types and their instances.
 * <p>
 * Each recorded expectation is intended to match one or more method or constructor invocations, that we expect will occur during the
 * execution of code under test, and which need to exhibit some specific behavior for the purposes of the test.
 * When a match is detected, the recorded {@linkplain #result result} (if any) is returned to the caller.
 * Alternatively, a recorded exception/error is thrown, or an arbitrary {@linkplain Delegate delegate} method is executed.
 * <p>
 * Expectations are recorded simply by invoking the desired method or constructor on the mocked type/instance, during the initialization of
 * an <code>Expectations</code> object.
 * This is done by instantiating an anonymous subclass containing an instance initialization body, or as we call it, an
 * <em>expectation block</em>:
 * <pre>{@code
 * // <em>Record</em> one or more expectations on available mocked types/instances.
 * new Expectations() {{
 *    <strong>mock1</strong>.expectedMethod(anyInt); result = 123; times = 2;
 *    <strong>mock2</strong>.anotherExpectedMethod(1, "test"); result = "Abc";
 * }};
 *
 * // Exercise tested code, with previously recorded expectations now available for <em>replay</em>.
 * codeUnderTest.doSomething();
 * }</pre>
 * During replay, invocations matching a recorded expectation must occur at least <em>once</em> (unless specified otherwise);
 * if, by the end of the test, no matching invocation occurred for a given recorded expectation, the test will fail with a
 * <code>MissingInvocation</code> error.
 * <p>
 * When multiple expectations are recorded, matching invocations are allowed to occur in a <em>different</em> order.
 * So, the order in which expectations are recorded is not significant.
 * <p>
 * Besides the special {@link #result} field already mentioned, there are several other fields and methods which can be used inside the
 * expectation block:
 * a) {@link #returns(Object, Object, Object...)}, a convenience method for returning a <em>sequence</em> of values;
 * b) argument matchers such as {@link #anyInt}, {@link #anyString}, {@link #withNotNull()}, etc., which relax or constrain the matching of
 * argument values;
 * c) the {@link #times}, {@link #minTimes}, and {@link #maxTimes} fields, which relax or constrain the expected and/or allowed number of
 * matching invocations.
 * <p>
 * By default, the exact instance on which instance method invocations will occur during replay is <em>not</em> verified to be the same as
 * the instance used when recording the expectation.
 * That said, instance-specific matching can be obtained by declaring the mocked type as {@linkplain Injectable @Injectable}, or by
 * declaring multiple mock fields and/or mock parameters of the same mocked type (so that separate expectations can be recorded for each
 * mock instance).
 * <p>
 * Invocations occurring during replay, whether they matched recorded expectations or not, can be explicitly verified <em>after</em>
 * exercising the code under test.
 * To that end, we use a set of complementary base classes: {@link Verifications}, {@link VerificationsInOrder}, and
 * {@link FullVerifications}.
 * Similar to expectation blocks, these classes allow us to create <em>verification</em> blocks.
 *
 * @see #Expectations()
 * @see #Expectations(Object...)
 * @see <a href="http://jmockit.github.io/tutorial/Mocking.html#expectation" target="tutorial">Tutorial</a>
 */
public class Expectations extends Invocations
{
   /**
    * A value assigned to this field will be taken as the result for the expectation that is being recorded.
    * <p>
    * If the value is a {@link Throwable} then it will be <em>thrown</em> when a matching invocation later occurs.
    * Otherwise, it's assumed to be a <em>return value</em> for a non-<code>void</code> method, and will be returned from a matching
    * invocation.
    * <p>
    * If no result is recorded for a given expectation, then all matching invocations will return the appropriate default value according
    * to the method return type:
    * <ul>
    * <li>Most <code>java.lang</code> types (<code>String</code>, <code>Object</code>, etc.): returns <code>null</code>.</li>
    * <li><code>java.math</code> types (<code>BigDecimal</code>, etc.): returns <code>null</code>.</li>
    * <li>Primitive/wrapper types: returns the standard default value (<code>false</code> for <code>boolean/Boolean</code>, <code>0</code> for
    * <code>int/Integer</code>, and so on).
    * </li>
    * <li><code>java.util.List</code>, <code>java.util.Collection</code>, or <code>java.lang.Iterable</code>: returns
    * {@link Collections#EMPTY_LIST}.</li>
    * <li><code>java.util.Iterator</code> or <code>java.util.ListIterator</code>: returns an empty iterator.</li>
    * <li><code>java.util.Set</code>: returns {@link Collections#EMPTY_SET}.</li>
    * <li><code>java.util.SortedSet</code>: returns an unmodifiable empty sorted set.</li>
    * <li><code>java.util.Map</code>: returns {@link Collections#EMPTY_MAP}.</li>
    * <li><code>java.util.SortedMap</code>: returns an unmodifiable empty sorted map.</li>
    * <li><code>java.util.Optional</code>: returns {@link Optional#empty()}.</li>
    * <li>Other reference types: returns a mocked instance through cascading.</li>
    * <li>Array types: returns an array with zero elements (empty) in each dimension.</li>
    * </ul>
    * When an expectation is recorded for a method which actually <em>returns</em> an exception or error (as opposed to <em>throwing</em>
    * one), then the {@link #returns(Object, Object, Object...)} method should be used instead, as it only applies to return values.
    * <p>
    * Assigning a value whose type differs from the method return type will cause an <code>IllegalArgumentException</code> to be thrown, unless
    * it can be safely converted to the return type.
    * One such conversion is from an array to a collection or iterator.
    * Another is from an array of at least two dimensions to a map, with the first dimension providing the keys and the second the values.
    * Yet another conversion is from a single value to a container type holding that value.
    * <p>
    * A sequence of <em>consecutive results</em> can be recorded simply by assigning the field multiple times for the same expectation.
    * Alternatively, the desired sequence of results for a single-valued return type can be recorded by assigning an array, an
    * {@link Iterable}, or an {@link Iterator} containing the individual results in order.
    * <p>
    * Results that depend on some programming logic can be provided through a {@linkplain Delegate} object assigned to the field.
    * This applies to <code>void</code> and non-<code>void</code> methods, as well as to constructors.
    *
    * @see <a href="http://jmockit.github.io/tutorial/Mocking.html#results" target="tutorial">Tutorial</a>
    */
   @Nullable
   protected Object result;

   /**
    * Registers one or more expectations recorded on available mocked types and/or mocked instances, as written inside the instance
    * initialization body of an anonymous subclass.
    *
    * @see #Expectations(Object...)
    * @see <a href="http://jmockit.github.io/tutorial/Mocking.html#expectation" target="tutorial">Tutorial</a>
    */
   protected Expectations() {
      RecordAndReplayExecution execution = new RecordAndReplayExecution(this, (Object[]) null);
      //noinspection ConstantConditions
      currentPhase = execution.getRecordPhase();
   }

   /**
    * Same as {@link #Expectations()}, except that one or more objects will be partially mocked according to the expectations recorded in
    * the expectation block.
    * <p>
    * During replay, any invocations to instance methods on these objects will execute real production code, unless a matching expectation
    * was recorded.
    *
    * @param objectsToBePartiallyMocked one or more objects to be partially mocked
    *
    * @throws IllegalArgumentException if given a <code>Class</code> object, or if given a value/instance of an interface, an annotation, an
    * array, a primitive/wrapper type, a synthetic class, or a {@linkplain java.lang.reflect.Proxy#isProxyClass(Class) proxy class}
    *
    * @see <a href="http://jmockit.github.io/tutorial/Mocking.html#partial" target="tutorial">Tutorial</a>
    */
   protected Expectations(@Nonnull Object... objectsToBePartiallyMocked) {
      RecordAndReplayExecution execution = new RecordAndReplayExecution(this, objectsToBePartiallyMocked);
      //noinspection ConstantConditions
      currentPhase = execution.getRecordPhase();
   }

   /**
    * Specifies that the previously recorded method invocation will return a given sequence of values during replay.
    * <p>
    * Calling this method is equivalent to assigning the {@link #result} field two or more times in sequence, or assigning it a single time
    * with an array or iterable containing the same sequence of values.
    * <p>
    * Certain data conversions will be applied, depending on the return type of the recorded method:
    * <ol>
    * <li>If the return type is iterable and can receive a {@link List} value, then the given sequence of values will be converted into an
    * <code>ArrayList</code>; this list will then be returned by matching invocations at replay time.</li>
    * <li>If the return type is <code>SortedSet</code> or a sub-type, then the given sequence of values will be converted into a
    * <code>TreeSet</code>; otherwise, if it is <code>Set</code> or a sub-type, then a <code>LinkedHashSet</code> will be created to hold the values;
    * the set will then be returned by matching invocations at replay time.</li>
    * <li>If the return type is <code>Iterator</code> or a sub-type, then the given sequence of values will be converted into a <code>List</code>
    * and the iterator created from this list will be returned by matching invocations at replay time.</li>
    * <li>If the return type is an array, then the given sequence of values will be converted to an array of the same type, which will be
    * returned by matching invocations at replay time.</li>
    * </ol>
    * The current expectation will have its upper invocation count automatically set to the total number of values specified to be returned.
    * This upper limit can be overridden through the <code>maxTimes</code> field, if necessary.
    *
    * @param firstValue the first value to be returned at replay time
    * @param secondValue the second value to be returned at replay time
    * @param remainingValues any remaining values to be returned, in the same order
    *
    * @see <a href="http://jmockit.github.io/tutorial/Mocking.html#results" target="tutorial">Tutorial</a>
    */
   protected final void returns(@Nullable Object firstValue, @Nullable Object secondValue, @Nonnull Object... remainingValues) {
      int n = remainingValues.length;
      Object[] values = new Object[2 + n];
      values[0] = firstValue;
      values[1] = secondValue;
      System.arraycopy(remainingValues, 0, values, 2, n);

      ((RecordPhase) currentPhase).addSequenceOfReturnValues(values);
   }
}