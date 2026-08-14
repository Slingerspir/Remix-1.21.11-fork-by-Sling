package cn.remix.management;

import cn.remix.util.IMinecraft;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FriendManager implements IMinecraft {
    @Getter
    private static final CopyOnWriteArrayList<String> friends = new CopyOnWriteArrayList<>();
    @Getter
    private static final CopyOnWriteArrayList<String> enemies = new CopyOnWriteArrayList<>();
    private final File friendsFile = new File("Remix", "friends.txt");
    private final File enemiesFile = new File("Remix", "enemies.txt");

    public FriendManager() {
        instance.getEventManager().register(this);
        load();
    }

    public boolean addFriend(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) return false;

        boolean changed = addName(friends, normalized);
        boolean removedEnemy = removeName(enemies, normalized);
        if (changed || removedEnemy) save();
        return changed;
    }

    public boolean removeFriend(String name) {
        boolean changed = removeName(friends, name);
        if (changed) save();
        return changed;
    }

    public boolean isFriend(String name) {
        return containsName(friends, name);
    }

    public boolean addEnemy(String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty()) return false;

        boolean changed = addName(enemies, normalized);
        boolean removedFriend = removeName(friends, normalized);
        if (changed || removedFriend) save();
        return changed;
    }

    public boolean removeEnemy(String name) {
        boolean changed = removeName(enemies, name);
        if (changed) save();
        return changed;
    }

    public boolean isEnemy(String name) {
        return containsName(enemies, name);
    }

    private void load() {
        friends.clear();
        enemies.clear();
        readNames(friendsFile, friends);
        readNames(enemiesFile, enemies);
    }

    private void save() {
        writeNames(friendsFile, friends);
        writeNames(enemiesFile, enemies);
    }

    private void readNames(File file, CopyOnWriteArrayList<String> target) {
        if (!file.exists()) return;
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                addName(target, line);
            }
        } catch (IOException ignored) {
        }
    }

    private void writeNames(File file, Collection<String> names) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) return;
        List<String> sorted = names.stream()
                .map(this::normalizeName)
                .filter(name -> !name.isEmpty())
                .distinct()
                .sorted(Comparator.comparing(String::toLowerCase))
                .toList();
        try {
            Files.write(file.toPath(), sorted, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private boolean addName(CopyOnWriteArrayList<String> names, String name) {
        String normalized = normalizeName(name);
        if (normalized.isEmpty() || containsName(names, normalized)) return false;
        names.add(normalized);
        return true;
    }

    private boolean removeName(CopyOnWriteArrayList<String> names, String name) {
        String normalized = normalizeName(name);
        return names.removeIf(value -> value.equalsIgnoreCase(normalized));
    }

    private boolean containsName(CopyOnWriteArrayList<String> names, String name) {
        String normalized = normalizeName(name);
        return names.stream().anyMatch(value -> value.equalsIgnoreCase(normalized));
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
