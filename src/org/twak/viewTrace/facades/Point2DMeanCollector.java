package org.twak.viewTrace.facades;

import java.util.Collections;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import javax.vecmath.Point2d;

import org.twak.utils.Pair;

public class Point2DMeanCollector implements Collector<Point2d, Pair<Point2d, Integer>, Point2d> {

	@Override
	public Supplier<Pair<Point2d, Integer>> supplier() {
		return () -> new Pair<> (new Point2d(), 0);
	}

	@Override
	public BiConsumer<Pair<Point2d, Integer>, Point2d> accumulator() {
		return (pair, pt) -> { pair.first().add(pt); pair.set2 ( pair.second() + 1 ); };
	}

	@Override
	public BinaryOperator<Pair<Point2d, Integer>> combiner() {
		return (a,b) -> new Pair<>(
				new Point2d(a.first().x + b.first().x, b.first().y + b.first().y),
				a.second() + b.second()
					);
	}

	@Override
	public Function<Pair<Point2d, Integer>, Point2d> finisher() {
		return pair -> new Point2d(pair.first().x / pair.second(), pair.first().y / pair.second() );
	}

	@Override
	public Set<java.util.stream.Collector.Characteristics> characteristics() {
		return Collections.emptySet();
	}
}
