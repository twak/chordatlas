package org.twak.viewTrace;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class InAxDoubleArray implements Collector<double[], double[], double[]> {

	@Override
	public BiConsumer<double[], double[]> accumulator() {
		return (minMax, v) -> {
			for (double d : v) {
				minMax[0] = Math.min(d, minMax[0]);
				minMax[1] = Math.max(d, minMax[1]);
			}
		};
	}

	@Override
	public Set<java.util.stream.Collector.Characteristics> characteristics() {
		return EnumSet.of(Characteristics.UNORDERED);
	}

	@Override
	public BinaryOperator<double[]> combiner() {
		return (a,b) -> new double[] { Math.min ( a[0], b[0] ), Math.max ( a[1], b[1] ) };
	}

	@Override
	public Function<double[], double[]> finisher() {
		return a -> a;
	}

	@Override
	public Supplier<double[]> supplier() {
		return () -> new double[] { Double.MAX_VALUE, -Double.MAX_VALUE };
	}
}
