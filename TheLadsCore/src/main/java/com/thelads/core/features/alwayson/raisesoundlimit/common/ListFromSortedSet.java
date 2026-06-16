package com.thelads.core.features.alwayson.raisesoundlimit.common;

import com.google.common.collect.Iterators;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class ListFromSortedSet<T> implements List<T> {
    private final SortedSet<T> delegate;
    private final ArrayList<T> listBacking = new ArrayList<>();

    public ListFromSortedSet(SortedSet<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate);
        this.listBacking.addAll(delegate);
    }

    @Override
    public int size() {
        return this.delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return this.delegate.contains(o);
    }

    @Override
    public @NonNull Iterator<T> iterator() {
        final Iterator<T> it = this.delegate.iterator();
        return new Iterator<T>() {
            private T current = null;
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }
            @Override
            public T next() {
                current = it.next();
                return current;
            }
            @Override
            public void remove() {
                it.remove();
                if (current != null) {
                    listBacking.remove(current);
                }
            }
        };
    }

    @Override
    public @NonNull Object[] toArray() {
        return this.delegate.toArray();
    }

    @Override
    public <T1> @NonNull T1[] toArray(@NonNull T1[] a) {
        return this.delegate.toArray(a);
    }

    @Override
    public boolean add(T t) {
        boolean added = this.delegate.add(t);
        if (added) {
            this.listBacking.clear();
            this.listBacking.addAll(this.delegate);
        }
        return added;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = this.delegate.remove(o);
        if (removed) {
            this.listBacking.remove(o);
        }
        return removed;
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> c) {
        return this.delegate.containsAll(c);
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends T> c) {
        boolean added = this.delegate.addAll(c);
        if (added) {
            this.listBacking.clear();
            this.listBacking.addAll(this.delegate);
        }
        return added;
    }

    @Override
    public boolean addAll(int index, @NonNull Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> c) {
        boolean removed = this.delegate.removeAll(c);
        if (removed) {
            this.listBacking.removeAll(c);
        }
        return removed;
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> c) {
        boolean removed = this.delegate.retainAll(c);
        if (removed) {
            this.listBacking.retainAll(c);
        }
        return removed;
    }

    @Override
    public void clear() {
        this.delegate.clear();
        this.listBacking.clear();
    }

    @Override
    public T get(int index) {
        return this.listBacking.get(index);
    }

    @Override
    public T set(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        return this.listBacking.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NonNull ListIterator<T> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NonNull ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NonNull List<T> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(@NonNull UnaryOperator<T> operator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sort(@Nullable Comparator<? super T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public @NonNull Spliterator<T> spliterator() {
        return this.delegate.spliterator();
    }

    @Override
    public void addFirst(T t) {
        this.delegate.addFirst(t);
        this.listBacking.clear();
        this.listBacking.addAll(this.delegate);
    }

    @Override
    public void addLast(T t) {
        this.delegate.addLast(t);
        this.listBacking.clear();
        this.listBacking.addAll(this.delegate);
    }

    @Override
    public T getFirst() {
        return this.delegate.getFirst();
    }

    @Override
    public T getLast() {
        return this.delegate.getLast();
    }

    @Override
    public T removeFirst() {
        T t = this.delegate.removeFirst();
        this.listBacking.clear();
        this.listBacking.addAll(this.delegate);
        return t;
    }

    @Override
    public T removeLast() {
        T t = this.delegate.removeLast();
        this.listBacking.clear();
        this.listBacking.addAll(this.delegate);
        return t;
    }

    @Override
    public List<T> reversed() {
        return new ListFromSortedSet<T>(this.delegate.reversed());
    }

    @Override
    public <T1> T1[] toArray(@NonNull IntFunction<T1[]> generator) {
        return this.delegate.toArray(generator);
    }

    @Override
    public boolean removeIf(@NonNull Predicate<? super T> filter) {
        boolean removed = this.delegate.removeIf(filter);
        if (removed) {
            this.listBacking.removeIf(filter);
        }
        return removed;
    }

    @Override
    public @NonNull Stream<T> stream() {
        return this.delegate.stream();
    }

    @Override
    public @NonNull Stream<T> parallelStream() {
        return this.delegate.parallelStream();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        this.delegate.forEach(action);
    }

    @Override
    public int hashCode() {
        return this.delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    public String toString() {
        return "ListFromSortedSet[" + this.delegate.toString() + "]";
    }
}
