package ru.javawebinar.topjava.util;

import javafx.util.Pair;
import ru.javawebinar.topjava.model.UserMeal;
import ru.javawebinar.topjava.model.UserMealWithExcess;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class UserMealsUtil {
    public static void main(String[] args) {
        List<UserMeal> meals = Arrays.asList(
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 30, 10, 0), "Завтрак", 500),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 30, 13, 0), "Обед", 1000),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 30, 20, 0), "Ужин", 500),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 0, 0), "Еда на граничное значение", 100),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 10, 0), "Завтрак", 1000),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 13, 0), "Обед", 500),
                new UserMeal(LocalDateTime.of(2020, Month.JANUARY, 31, 20, 0), "Ужин", 410)
        );

        List<UserMealWithExcess> mealsTo = filteredByCycles(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000);
        System.out.println("=By forEach:");
        mealsTo.forEach(System.out::println);

        System.out.println("=By streams:");
        System.out.println(filteredByStreams(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000));

        System.out.println("=By forEach one pass:");
        System.out.println(filteredByCyclesOnePass(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000));

        System.out.println("=By streams one pass:");
        System.out.println(filteredByStreamsOnePass(meals, LocalTime.of(7, 0), LocalTime.of(12, 0), 2000));
    }

    public static List<UserMealWithExcess> filteredByCycles(List<UserMeal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        Map<LocalDate, Integer> counter = new HashMap<>();
        meals.forEach(m -> counter.merge(m.getDateTime().toLocalDate(), m.getCalories(), Integer::sum));

        List<UserMealWithExcess> result = new ArrayList<>();

        meals.forEach(meal -> {
            if (TimeUtil.isBetweenHalfOpen(meal.getDateTime().toLocalTime(), startTime, endTime)) {
                result.add(mapToDTO(meal, counter.getOrDefault(meal.getDateTime().toLocalDate(), 0) > caloriesPerDay));
            }
        });

        return result;
    }

    public static List<UserMealWithExcess> filteredByStreams(List<UserMeal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        final Map<LocalDate, Integer> counter = meals
                .stream()
                .collect(Collectors.toMap(m -> m.getDateTime().toLocalDate(), UserMeal::getCalories, Integer::sum));

        return meals.stream()
                .filter(m -> TimeUtil.isBetweenHalfOpen(m.getDateTime().toLocalTime(), startTime, endTime))
                .map(m -> mapToDTO(m,
                        counter.getOrDefault(m.getDateTime().toLocalDate(), 0) > caloriesPerDay))
                .collect(Collectors.toList());
    }

    public static List<UserMealWithExcess> filteredByCyclesOnePass(List<UserMeal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        Map<LocalDate, Integer> counter = new HashMap<>();
        List<UserMeal> filtered = new ArrayList<>();

        meals.forEach(meal -> {
            final LocalDateTime dateTime = meal.getDateTime();
            if (TimeUtil.isBetweenHalfOpen(dateTime.toLocalTime(), startTime, endTime)) {
                filtered.add(meal);
            }

            counter.merge(meal.getDateTime().toLocalDate(), meal.getCalories(), Integer::sum);
        });

        List<UserMealWithExcess> result = new ArrayList<>();

        filtered.forEach(m -> result.add(mapToDTO(m,
                counter.getOrDefault(m.getDateTime().toLocalDate(), 0) > caloriesPerDay)));

        return result;
    }

    public static List<UserMealWithExcess> filteredByStreamsOnePass(List<UserMeal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        return meals.stream().collect(new Collector<UserMeal, Map<LocalDate, Pair<List<UserMeal>, Integer>>, List<UserMealWithExcess>>() {
            @Override
            public Supplier<Map<LocalDate, Pair<List<UserMeal>, Integer>>> supplier() {
                return HashMap::new;
            }

            @Override
            public BiConsumer<Map<LocalDate, Pair<List<UserMeal>, Integer>>, UserMeal> accumulator() {
                return (map, meal) -> {
                    final LocalDateTime dateTime = meal.getDateTime();
                    final Pair<List<UserMeal>, Integer> pair = map.get(dateTime.toLocalDate());

                    List<UserMeal> key;
                    Integer value;

                    if (pair != null) {
                        key  = pair.getKey();
                        value = pair.getValue();
                    } else {
                        key = new ArrayList<>();
                        value = 0;
                    }

                    if (TimeUtil.isBetweenHalfOpen(dateTime.toLocalTime(), startTime, endTime)) {
                        key.add(meal);
                    }

                    value += meal.getCalories();

                    map.put(dateTime.toLocalDate(), new Pair<>(key, value));
                } ;
            }

            @Override
            public BinaryOperator<Map<LocalDate, Pair<List<UserMeal>, Integer>>> combiner() {
                return (a, b) -> {
                    b.forEach(a::put);
                    return a;
                };
            }

            @Override
            public Function<Map<LocalDate, Pair<List<UserMeal>, Integer>>, List<UserMealWithExcess>> finisher() {
                return map -> map.values()
                        .stream()
                        .map(p -> mapListToDTO(p.getKey(), p.getValue() > caloriesPerDay))
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
            }

            @Override
            public Set<Characteristics> characteristics() {
                return new HashSet<>(Collections.singletonList(Characteristics.UNORDERED));
            }
        });
    }

    private static UserMealWithExcess mapToDTO(UserMeal meal, boolean excess) {
        return new UserMealWithExcess(meal.getDateTime(), meal.getDescription(), meal.getCalories(), excess);
    }

    private static List<UserMealWithExcess> mapListToDTO(List<UserMeal> meal, boolean excess) {
        return meal.stream().map(um -> mapToDTO(um, excess)).collect(Collectors.toList());
    }
}
