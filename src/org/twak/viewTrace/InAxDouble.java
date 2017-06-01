package org.twak.viewTrace;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class InAxDouble implements Collector<Double, double[], double[]> {

	@Override
	public BiConsumer<double[], Double> accumulator() {
		return (minMax, v) -> {
			minMax[0] = Math.min(v, minMax[0]);
			minMax[1] = Math.max(v, minMax[1]);
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
