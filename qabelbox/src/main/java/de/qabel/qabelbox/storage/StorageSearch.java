package de.qabel.qabelbox.storage;

import java.util.ArrayList;
import java.util.List;

import de.qabel.qabelbox.exceptions.QblStorageException;

/**
 * Represents a search across a BoxVolume path.
 */
public class StorageSearch {

    private BoxNavigation navigation;
    private List<BoxObject> results;

    /**
     * Inits the results with all available files and directories.
     *
     * @param navigation The path from which on the search should begin
     * @throws QblStorageException
     */
    public StorageSearch(BoxNavigation navigation) throws QblStorageException {
        this.navigation = navigation;
        results = collectAll();
    }

    /**
     * Construct a search using a resultset and don't hit the storage volume.
     *
     * @param results
     */
    public StorageSearch(List<BoxObject> results) {
        this.results = results;
    }

    /**
     * The flat list of the current resultset.
     *
     * @return The filtered list, all files and dirs if no filter was applied.
     */
    public List<BoxObject> getResults() {
        return results;
    }

    public static boolean isValidSearchTerm(String name) {
        return name != null && !"".equals(name.trim());
    }

    public StorageSearch filterByNameCaseSensitive(String name) {
        return filterByName(name, true);
    }

    public StorageSearch filterByNameCaseInsensitive(String name) {
        return filterByName(name, false);
    }

    public StorageSearch filterByName(String name) {
        return filterByNameCaseInsensitive(name);
    }

    public StorageSearch filterByName(String name, boolean caseSensitive) {

        if (!isValidSearchTerm(name)) {
            return this;
        }

        if (!caseSensitive) {
            name = name.toLowerCase();
        }

        List<BoxObject> filtered = new ArrayList<>();

        for (BoxObject o : results) {
            String objKey = caseSensitive ? o.name : o.name.toLowerCase();

            if (objKey.indexOf(name) >= 0) {
                filtered.add(o);
            }
        }

        results = filtered;

        return this;
    }

    public StorageSearch filterByMaximumSize(long size) {
        return filterBySize(size, false);
    }

    public StorageSearch filterByMinimumSize(long size) {
        return filterBySize(size, true);
    }

    public StorageSearch filterBySize(long size, boolean minSize) {

        List<BoxObject> filtered = new ArrayList<>();

        for (BoxObject o : results) {
            if (o instanceof BoxFile) {
                BoxFile f = (BoxFile) o;

                if ((minSize && f.size >= size) || (!minSize && f.size <= size)) {
                    filtered.add(o);
                }
            }
        }

        results = filtered;

        return this;
    }

    public StorageSearch filterOnlyFiles() {

        List<BoxObject> filtered = new ArrayList<>();
        filtered.addAll(StorageSearch.toBoxFiles(results));
        results = filtered;

        return this;
    }

    public static List<BoxFile> toBoxFiles(List<BoxObject> lst) {
        List<BoxFile> ret = new ArrayList<>();

        for (BoxObject o : lst) {
            if (o instanceof BoxFile) {
                ret.add((BoxFile) o);
            }
        }

        return ret;
    }

    public StorageSearch filterOnlyDirectories() {

        List<BoxObject> filtered = new ArrayList<>();
        filtered.addAll(StorageSearch.toBoxFolders(results));
        results = filtered;

        return this;
    }

    public static List<BoxFolder> toBoxFolders(List<BoxObject> lst) {
        List<BoxFolder> ret = new ArrayList<>();

        for (BoxObject o : lst) {
            if (o instanceof BoxFolder) {
                ret.add((BoxFolder) o);
            }
        }

        return ret;
    }


    private List<BoxObject> collectAll() throws QblStorageException {
        List<BoxObject> lst = new ArrayList<>();

        addAll(lst);

        return lst;
    }

    private void addAll(List<BoxObject> lst) throws QblStorageException {

        for (BoxFile file : navigation.listFiles()) {
            lst.add(file);
        }

        for (BoxFolder folder : navigation.listFolders()) {
            lst.add(folder);

            navigation.navigate(folder);
            addAll(lst);
            navigation.navigateToParent();
        }
    }

}
