package com.sap.demo.tc.wd.tut.table.sofi.wd.comp.tutorial.tutorial.util;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import com.sap.dictionary.runtime.ICoreComponentType;
import com.sap.dictionary.runtime.IDataType;
import com.sap.dictionary.runtime.container.ICctContainer;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDAbstractDropDownByIndex;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDAbstractDropDownByKey;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDAbstractInputField;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDAbstractTableColumn;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDCaption;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDCheckBox;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDLink;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDProgressIndicator;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDRadioButton;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTable;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTableCellEditor;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTableColumn;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTableColumnGroup;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTextEdit;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTextView;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.WDTableColumnSortDirection;
import com.sap.tc.webdynpro.progmodel.api.IWDAction;
import com.sap.tc.webdynpro.progmodel.api.IWDCustomEvent;
import com.sap.tc.webdynpro.progmodel.api.IWDNode;
import com.sap.tc.webdynpro.progmodel.api.IWDNodeElement;
import com.sap.tc.webdynpro.progmodel.api.IWDNodeInfo;
import com.sap.tc.webdynpro.progmodel.api.IWDViewElement;
import com.sap.tc.webdynpro.services.sal.localization.api.WDResourceHandler;

/**
 * Helper class that makes a Web Dynpro table UI element sortable (column-wise).
 * Updated version for NetWeaver 7.1.
 * <p>
 * It is intended that this class can be used as a direct replacement for the
 * <code>TableSorter</code> from the corresponding 7.0 tuturial. Differences
 * and new features:
 * <ul>
 *  <li> All string comparisons can be done using a Collator instead of a simple
 *   <code>String.equals(Object)</code>. This is switched off per default, it
 *   can be switched on globally via {@link #setCollatorStrength(com.sap.test.tc.wd.tut.table.sofi2.util.TableSorter.CollatorStrength) 
 *   setCollatorStrength(CollatorStrength)}. You can create a comparator with
 *   different strength yourself via {@link #createStringComparator(com.sap.test.tc.wd.tut.table.sofi2.util.TableSorter.CollatorStrength) 
 *   createStringComparator(CollatorStrength)} and pass it in.
 *  <li> Core Component Types are supported. They are compared by comparing the
 *   content component.
 *  <li> You can create a matching comparator for a given data type using
 *   {@link #createComparator(IDataType, com.sap.test.tc.wd.tut.table.sofi2.util.TableSorter.CollatorStrength)
 *   createComparator(IDataType, CollatorStrength)}
 *  <li> You can exchange the comparator for a given column at any time using
 *   {@link #setComparator(String, Comparator)}.
 * </ul>
 * The construction of the actual element comparator is delayed until the sort
 * itself. This ensures that the above methods really show effects. 
 */
public class TableSorter extends TableHelper
{
  private static final CollatorStrength DEFAULT_COLLATOR_STRENGTH = CollatorStrength.IDENTICAL;

  public enum CollatorStrength {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    IDENTICAL
  };

  /**
   * Creates a matching comparator for the given data type. This comparator
   * takes care of <code>null</code> which is considered to be smaller than any
   * real value. For Core Component Types a comparator is created which compares
   * the content component using a recursively created comparator. For Strings
   * a special string comparator supporting Collator is created. Comparables
   * are compared using their <code>compareTo</code> method.
   */
  public static Comparator<?> createComparator(IDataType type, CollatorStrength strength) {
    if (type instanceof ICoreComponentType) {
      ICoreComponentType cct = (ICoreComponentType)type;
      Comparator<?> contentComparator = createComparator(cct.getComponent(ICoreComponentType.CONTENT).getType(), strength);
      if (contentComparator != null) {
        return new CctComparator(contentComparator);
      }
    }
    Class<?> valueClass = type.getAssociatedClass();
    if (valueClass == String.class) {
      return createStringComparator(strength);
    } 
    return new SimpleComparator<Object>(false);
  }

  /**
   * Creates a Comparator comparing Strings using a Collator with the given 
   * strength.
   * <p>
   * Note: The system does not use a <code>Collator</code> if the strength is
   * <code>IDENTICAL</code>.
   */
  public static Comparator<String> createStringComparator(CollatorStrength strength) {
    if (strength == CollatorStrength.IDENTICAL) {
      return new SimpleComparator<String>(false);
    }
    return new StringComparator(WDResourceHandler.getCurrentSessionLocale(), strength.ordinal());
  }

  /**
   * @param table
   * @param sortAction
   * @param comparators
   */
  public TableSorter(IWDTable table, IWDAction sortAction, 
          Map<String, Comparator<?>> comparators) {
    super(table);
    init(sortAction, comparators, null);
  }

  public TableSorter(IWDTable table, IWDAction sortAction, 
          Map<String, Comparator<?>> comparators, String...sortableColumns) {
    super(table);
    init(sortAction, comparators, sortableColumns);
  }

  /**
   * Returns the currently active collector strength
   */
  public CollatorStrength getCollatorStrength() {
    return collatorStrength;
  }

  /**
   * Sets the collector strength. All string comparators that the sorter creates
   * afterwards will have this strength.
   * <p>
   * Note: The system does not use a <code>Collator</code> if the strength is
   * <code>IDENTICAL</code>.
   */
  public void setCollatorStrength(CollatorStrength collatorStrength) {
    this.collatorStrength = collatorStrength;
  }

  /**
   * Sets the comparator for the given column. May be called at any time.
   * Afterwards the TableSorter will sort this column using the given
   * comparator.
   * 
   * @param columnId the name of the table column
   * @param comparator the new comparator. It may be a Comparator comparing the
   *                attribute values behind this column or it may be a
   *                {@link NodeElementByAttributeComparator} which is used
   *                directly without any modification
   */
  public void setComparator(String columnId, Comparator<?> comparator) {
    IWDTableColumn column = (IWDTableColumn)table.getView().getElement(columnId);
    if (column == null) {
      throw new IllegalArgumentException("there is no column named " + columnId);
    }
    if (!setUpColumn(column, comparator)) {
      throw new IllegalArgumentException("column " + columnId + " has no sortable attribute");
    }
  }

  /**
   * This method must be called from the event handler of this table sorter's
   * sort action. It performs the actual sort operation.
   */
  public void sort(IWDCustomEvent wdEvent, IWDNode dataSource) {
    // find the things we need
    String columnId = wdEvent.getString("selectedColumn");
    WDTableColumnSortDirection direction = WDTableColumnSortDirection.valueOf(
            wdEvent.getString("sortDirection"));
    IWDTableColumn column = (IWDTableColumn)table.getView().getElement(columnId);
    NodeElementByAttributeComparator elementComparator = getComparator(column, dataSource);
    if (elementComparator == null) {
      // not a sortable column
      column.setSortState(WDTableColumnSortDirection.NOT_SORTABLE);
      return;
    }

    // sorting
    elementComparator.setSortDirection(direction);
    dataSource.sortElements(elementComparator);
  }

  /**
   * Initialisation stuff
   */
  private void init(IWDAction sortAction,
          Map<String, Comparator<?>> comparators, String[] sortableColumns) {
    if (comparators == null) {
      comparators = Collections.emptyMap();
    }
    Collection<IWDTableColumn> columns = getColumns();
    // set up the sortable columns
    if (sortableColumns == null) {
      // no list -> set up all columns
      for (IWDTableColumn column : columns) {
        setUpColumn(column, comparators.get(column.getId()));
      }
    } else {
      // set up only the named columns 
      Map<String, IWDTableColumn> columnsByName = new HashMap<String, IWDTableColumn>();
      for (IWDTableColumn column : columns) {
        columnsByName.put(column.getId(), column);
      }
      for (String columnName : sortableColumns) {
        IWDTableColumn column = columnsByName.get(columnName);
        if (column != null) {
          setUpColumn(column, comparators.get(columnName));
        }
      }
    }

    if (sortAction != null) {
      // set up the sort action
      table.setOnSort(sortAction);
      table.mappingOfOnSort().addSourceMapping(IWDTable.IWDOnSort.COL,
              "selectedColumn");
      table.mappingOfOnSort().addSourceMapping(IWDTable.IWDOnSort.DIRECTION,
              "sortDirection");
    } else if (table.getOnSort() == null) {
      throw new IllegalArgumentException("Sort action must be given");
    }
  }

  private boolean setUpColumn(IWDTableColumn column, Comparator<?> comparator) {
    boolean sortable = isSortable(column, comparator);
    if (sortable) {
      column.setSortState(WDTableColumnSortDirection.NONE);
      // remember the column and the given comparator
      comparatorForColumn.put(column, comparator);
    } else {
      column.setSortState(WDTableColumnSortDirection.NOT_SORTABLE);
    }
    return sortable;
  }

  /**
   * Determines and caches the comparator for the given column. Expects the
   * column binding to be valid.
   */
  private NodeElementByAttributeComparator getComparator(IWDTableColumn column, IWDNode node) {
    if (!comparatorForColumn.containsKey(column)) {
      // so this column is not sortable
      return null;
    }
    // see what comparator we got so far
    Comparator<?> comparator = comparatorForColumn.get(column.getId());
    if (comparator instanceof NodeElementByAttributeComparator) {
      // already known
      return (NodeElementByAttributeComparator)comparator;
    }
    // none or one for the attribute, set up the NodeElement comparator
    String attributeName = getRelativeBindingOfPrimaryProperty(column);
    String[] subnodes = null;
    if (attributeName.indexOf('.') >= 0) {
      // attribute not immediately below data source
      String[] tokens = tokenize(attributeName, ".");
      subnodes = new String[tokens.length - 1];
      System.arraycopy(tokens, 0, subnodes, 0, subnodes.length);
      attributeName = tokens[subnodes.length];
    }
    return new NodeElementByAttributeComparator(node, attributeName, comparator, false, subnodes);
  }

  /**
   * Returns <code>true</code> if the column is sortable.
   */
  private boolean isSortable(IWDTableColumn column, Comparator<?> comparator) {
    if (comparator instanceof NodeElementByAttributeComparator) {
      // element comparator is given, let's assume the column is sortable
      return true;
    }
    return getRelativeBindingOfPrimaryProperty(column) != null;
  }

  /**
   * Map of table column to comparator (<code>ReversableComparator</code>)
   * used for sorting that column (sortable columns only).
   */
  private Map<IWDTableColumn, Comparator<?>> comparatorForColumn = new HashMap<IWDTableColumn, Comparator<?>>();

  /**
   * The collator strength to be used when setting up Comparators for Strings.
   */
  private CollatorStrength collatorStrength = DEFAULT_COLLATOR_STRENGTH;

  /**
   * A comparator that compares Strings using a <code>Collator</code>. Takes
   * care of <code>null</code>, it is considered to be smaller than any real
   * value.
   */
  private static class StringComparator implements Comparator<String>
  {
    private final Collator collator;

    StringComparator(Locale locale, int strength) {
      collator = Collator.getInstance(locale);
      collator.setStrength(strength);
    }

    public int compare(String string1, String string2) {
      if (string1 == null) {
        return string2 == null ? 0 : -1;
      } else if (string2 == null) {
        return 1;
      }
      return collator.compare(string1, string2);
    }
  }

  /**
   * A simple Comparator that can compare any Object. Takes care of
   * <code>null</code>, it is considered to be smaller than any real value.
   * If the Objects are not <code>Comparable</code> this is the only test.
   * Otherwise <code>{@link Comparable#compareTo(Object)}</code> is used.
   */
  private static class SimpleComparator<T> implements Comparator<T>
  {
    final boolean preliminary;
    
    SimpleComparator(boolean preliminary) {
      this.preliminary = preliminary;
    }

    public int compare(T o1, T o2) {
      if (o1 == null) {
        return o2 == null ? 0 : -1;
      } else if (o2 == null) {
        return 1;
      }
      if (o1 instanceof Comparable) {
        @SuppressWarnings("unchecked")
        Comparable<Object> comparable = (Comparable<Object>)o1;
        return comparable.compareTo(o2);
      }
      return 0;
    }
  }

  /**
   * A comparator comparing Core Component types. It compares the content using
   * the given comparator. Takes care of <code>null</code>, it is considered
   * to be smaller than any real value.
   */
  private static class CctComparator implements Comparator<ICctContainer>
  {
    private final Comparator<Object> contentComparator;

    @SuppressWarnings("unchecked")
    CctComparator(Comparator<?> contentComparator) {
      this.contentComparator = (Comparator<Object>)contentComparator;
    }

    public int compare(ICctContainer cct1, ICctContainer cct2) {
      if (cct1 == null) {
        return cct2 == null ? 0 : -1;
      } else if (cct2 == null) {
        return 1;
      }
      return contentComparator.compare(
              cct1.getComponentValue(ICctContainer.CONTENT), 
              cct2.getComponentValue(ICctContainer.CONTENT));
    }
  }
 
  /**
   * Generic comparator that compares node elements by a given attribute with
   * the help of a given comparator.
   */
  public final class NodeElementByAttributeComparator implements Comparator<IWDNodeElement>
  {
    /**
     * Creates a new comparator for the given attribute name that compares
     * values of that attribute according to the natural ordering of that
     * attribute's type (which must implement <code>java.lang.Comparable</code>).
     * 
     * @deprecated Create the comparator with help of a node, so that it is able
     *             to determine the attribute type and set up the correct
     *             comparator for it. This constructor will fail with CCTs for
     *             instance.
     */
    public NodeElementByAttributeComparator(String attributeName) {
      this(null, attributeName, null, false, (String[])null);
    }

    /**
     * Creates a new comparator for the given attribute name that compares
     * values of that attribute with the help of the given comparator. If no
     * comparator is given, the natural ordering of that attribute's type is
     * used.
     * 
     * @deprecated Create the comparator with help of a node, so that it is able
     *             to determine the attribute type and set up the correct
     *             comparator for it. This constructor will fail with CCTs for
     *             instance.
     */
    public NodeElementByAttributeComparator(String attributeName,
            Comparator<?> comparator) {
      this(null, attributeName, comparator, false, (String[])null);
    }

    /**
     * Creates a new comparator for the given attribute name that compares
     * values of that attribute either as objects (i.e. "in internal format") or
     * as text (i.e. "in external format") as indicated. The ordering is the
     * natural ordering of that attribute's type (which must implement
     * <code>java.lang.Comparable</code>) in case objects are compared or the
     * natural ordering of <code>java.lang.String</code> in case texts are
     * compared.
     * 
     * @deprecated Create the comparator with help of a node, so that it is able
     *             to determine the attribute type and set up the correct
     *             comparator for it. This constructor will fail with CCTs for
     *             instance.
     */
    public NodeElementByAttributeComparator(String attributeName,
            boolean compareAsText) {
      this(null, attributeName, null, compareAsText, (String[])null);
    }

    /**
     * Creates a new comparator for the given attribute name that compares
     * values of that attribute according to the natural ordering of that
     * attribute's type (which must implement <code>java.lang.Comparable</code>).
     * In addition it is possible to define the path to a child node with the
     * <code>java.util.Collection</code> subnodes. (List of child node names
     * in the correct order)
     * 
     * @deprecated Create the comparator with help of a node, so that it is able
     *             to determine the attribute type and set up the correct
     *             comparator for it. This constructor will fail with CCTs for
     *             instance.
     */
    public NodeElementByAttributeComparator(String attributeName,
            Collection<String> subnodes) {
      this(null, attributeName, null, false, subnodes);
    }

    /**
     * Creates a new comparator for the given attribute name that compares
     * values of that attribute with the help of the given comparator. If no
     * comparator is given, the natural ordering of that attribute's type is
     * used. In addition it is possible to define the path to a child node with
     * the <code>java.util.Collection</code> subnodes. (List of child node
     * names in the correct order)
     * 
     * @deprecated Create the comparator with help of a node, so that it is able
     *             to determine the attribute type and set up the correct
     *             comparator for it. This constructor will fail with CCTs for
     *             instance.
     */
    public NodeElementByAttributeComparator(String attributeName,
            Comparator<?> comparator, Collection<String> subnodes) {
      this(null, attributeName, comparator, false, subnodes);
    }

    /**
     * Creates a new comparator for the given attribute name that compares
     * values of that attribute either as objects (i.e. "in internal format") or
     * as text (i.e. "in external format") as indicated. The ordering is the
     * natural ordering of that attribute's type (which must implement
     * <code>java.lang.Comparable</code>) in case objects are compared or the
     * natural ordering of <code>java.lang.String</code> in case texts are
     * compared. In addition it is possible to define the path to a child node
     * with the <code>java.util.Collection</code> subnodes. (List of child
     * node names in the correct order)
     * 
     * @deprecated Create the comparator with help of a node, so that it is able
     *             to determine the attribute type and set up the correct
     *             comparator for it. This constructor will fail with CCTs for
     *             instance.
     */
    public NodeElementByAttributeComparator(String attributeName,
            boolean compareAsText, Collection<String> subnodes) {
      this(null, attributeName, null, compareAsText, subnodes);
    }

    /**
     * Creates a new comparator for the given attribute name that compares
     * values of that attribute either as objects (i.e. "in internal format") or
     * as text (i.e. "in external format") as indicated. The ordering is the
     * natural ordering of that attribute's type (which must implement
     * <code>java.lang.Comparable</code>) in case objects are compared or the
     * natural ordering of <code>java.lang.String</code> in case texts are
     * compared. In addition it is possible to define the path to a child node
     * with the <code>java.util.Collection</code> subnodes. (List of child
     * node names in the correct order).
     * <p>
     * The ordering is given by the following rules:
     * <ul>
     *  <li> If a comparator is given, it is used
     *  <li> If <code>compareAsText</code> is set, a comparator for the texts is
     * </ul>
     */
    public NodeElementByAttributeComparator(IWDNode node, String attributeName,
            Comparator<?> comparator, boolean compareAsText, Collection<String> subnodes) {
      this(node, attributeName, comparator, compareAsText,
              subnodes.toArray(new String[subnodes.size()]));
    }

    /**
     * Creates a new comparator for the given attribute name that compares
     * values of that attribute either as objects (i.e. "in internal format") or
     * as text (i.e. "in external format") as indicated. The ordering is the
     * natural ordering of that attribute's type (which must implement
     * <code>java.lang.Comparable</code>) in case objects are compared or the
     * natural ordering of <code>java.lang.String</code> in case texts are
     * compared. In addition it is possible to define the path to a child node
     * with the <code>java.util.Collection</code> subnodes. (List of child
     * node names in the correct order).
     * <p>
     * The ordering is given by the following rules:
     * <ul>
     *  <li> If a comparator is given, it is used
     *  <li> If <code>compareAsText</code> is set, a comparator for the texts is
     * </ul>
     */
    public NodeElementByAttributeComparator(IWDNode node, String attributeName,
            Comparator<?> comparator, boolean compareAsText, String[] subnodes) {
      if (attributeName == null) {
        throw new IllegalArgumentException("Attribute name must not be null");
      }
      this.accessor = compareAsText ? new TextAccessor(subnodes, attributeName)
                                    : new ValueAccessor<Object>(subnodes, attributeName);
      this.sortAscending = true;
      // this requires this.attributeName and this.subNodes
      this.comparator = determineComparator(node, compareAsText, comparator);
    }

    /**
     * Sets the sort direction of this comparator to the given direction. The
     * comparator sort in ascending order by default.
     * 
     * @see com.sap.tc.webdynpro.clientserver.uielib.standard.api.WDTableColumnSortDirection
     */
    public void setSortDirection(WDTableColumnSortDirection direction) {
      if (direction.equals(WDTableColumnSortDirection.UP)) {
        sortAscending = true;
      } else if (direction.equals(WDTableColumnSortDirection.DOWN)) {
        sortAscending = false;
      }
    }

    /**
     * Compares the given objects which must be instances of
     * <code>IWDNodeElement</code> according to the values of the attribute
     * given at construction time with the help of the comparator given at
     * construction time.
     * 
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     * @see com.sap.tc.webdynpro.progmodel.api.IWDNodeElement
     */
    public int compare(IWDNodeElement element1, IWDNodeElement element2) {
      Object attributeValue1 = accessor.get(element1);
      Object attributeValue2 = accessor.get(element2);
      if (sortAscending) {
        return comparator.compare(attributeValue1, attributeValue2);
      } else {
        return comparator.compare(attributeValue2, attributeValue1);
      }
    }

    Comparator<Object> determineComparator(IWDNode node, boolean compareAsText, Comparator<?> comparator) {
      if (comparator == null) {
        if (compareAsText) {
          comparator = createStringComparator(collatorStrength);
        } else if (node == null) {
          comparator = new SimpleComparator<Object>(false);
        } else {
          IDataType dataType = accessor.getDataType(node.getNodeInfo());
          comparator = createComparator(dataType, collatorStrength);
        }
      }
      @SuppressWarnings("unchecked")
      Comparator<Object> objectComparator = (Comparator<Object>)comparator;
      return objectComparator;
    }
    
    private Accessor<?> accessor;

    /**
     * Comparator used for comparing the attribute's values.
     */
    private Comparator<Object> comparator;

    /**
     * Sort direction (true = ascending order, false = descending order)
     */
    private boolean sortAscending;
  }
}
