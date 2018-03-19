package com.sap.demo.tc.wd.tut.table.sofi.wd.comp.tutorial.tutorial.util;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;

import com.sap.dictionary.runtime.IComponent;
import com.sap.dictionary.runtime.ICoreComponentType;
import com.sap.dictionary.runtime.IDataType;
import com.sap.dictionary.runtime.ISimpleType;
import com.sap.dictionary.runtime.container.ICctContainer;
import com.sap.tc.cmi.metadata.CMICardinality;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTable;
import com.sap.tc.webdynpro.clientserver.uielib.standard.api.IWDTableColumn;
import com.sap.tc.webdynpro.progmodel.api.IWDAction;
import com.sap.tc.webdynpro.progmodel.api.IWDAttributeInfo;
import com.sap.tc.webdynpro.progmodel.api.IWDMappingFilter;
import com.sap.tc.webdynpro.progmodel.api.IWDMessageManager;
import com.sap.tc.webdynpro.progmodel.api.IWDNode;
import com.sap.tc.webdynpro.progmodel.api.IWDNodeElement;
import com.sap.tc.webdynpro.progmodel.api.IWDNodeInfo;
import com.sap.tc.webdynpro.services.exceptions.WDNonFatalRuntimeException;
import com.sap.tc.webdynpro.services.sal.localization.api.WDResourceHandler;

/**
 * Helper class filtering a Web Dynpro table UI element. Updated version for
 * NetWeaver 7.1.
 * <p>
 * It is intended that this class can be used as a direct replacement for the
 * <code>TableFilter</code> from the corresponding 7.0 tuturial. However, the
 * filtering capabilities of Web Dynpro have improved dramatically. Therefore it
 * is much simpler to use than before. But the code has to be rewritten.
 * <p>
 * Differences and new features:
 * <ul>
 *  <li>Since <code>IWDMappingFilter</code> is used, filtering now happens
 *   without modifying the original node. No second node is required, no copying.
 *  <li>You do not have to maintain the filter attributes yourself. TableFilter
 *   does this automatically if there are none assigned yet.
 *  <li>Instead of supplying the column-attribute relation via the
 *   <code>Hashtable</code> in the constructor, you can later set it via
 *   {@link #setAttributeForColumn(String, String) setAttributeForColumn}.
 *  <li>You can define your own filter for a column by specifying a 
 *   {@link Matcher} via {@link #setFilter(String, boolean, Matcher)}
 *  <li>The {@link #filter()} method looks different. You don't have to supply
 *   nodes.
 * </ul>
 */
public final class TableFilter extends TableHelper
{
  /**
   * A matcher is supplied with a filter text and can tell whether a given value
   * matches this filter text.
   */
  public static interface Matcher<T>
  {
    /**
     * Sets the new filter value. This method is called when the application
     * calls {@link TableFilter#filter()} and the filter value has changed since
     * the last call.
     * 
     * @param filterValue the new filter value or <code>null</code> to switch
     *                off the filter
     */
    void setFilterValue(String filterValue);

    /**
     * Checks whether the given value matches the current filter value. Must
     * return <code>true</code> if the filter is switched off currently.
     */
    boolean matches(T value);
  }

  /**
   * The standard constructor. If the filter is not modified afterwards, it
   * filters all columns by their primary binding if this binding is to an
   * attribute of the source node or some child thereof.
   * 
   * @param table The table to filter, must be delivered
   * @param filterAction The action which is to be fired when filtering. It may
   *                be <code>null</code> when the action has already been
   *                assigned at design-time.
   * @param sourceNode The source node of the table, must be delivered
   */
  public TableFilter(IWDTable table, IWDAction filterAction, IWDNode sourceNode) {
    super(table);
    if (sourceNode == null)
      throw new IllegalArgumentException("SourceNode must be given");
    this.sourceNode = sourceNode;

    // process all table columns
    for (IWDTableColumn tableColumn : getColumns()) {
      Column column = new Column(tableColumn);
      columnsByName.put(tableColumn.getId(), column);
    }

    // activate the filter
    if (filterAction != null) {
      table.setOnFilter(filterAction);
      filterAction.setEnabled(true);
    } else if (table.getOnFilter() == null) {
      throw new IllegalArgumentException("filterAction must be given");
    }
  }

  /**
   * The original constructor. The only difference to the
   * {@linkplain #TableFilter(IWDTable, IWDAction, IWDNode) standard constructor}
   * is the Hashtable for the column-attribute mapping. You can achieve the same
   * by using the standard construction and calling
   * {@link #setAttributeForColumn(String, String)} for each column.
   * 
   * @param table The table to filter, must be delivered
   * @param filterAction The action which is to be fired when filtering. It may
   *                be <code>null</code> when the action has already been
   *                assigned at design-time.
   * @param sourceNode The source node of the table, must be delivered
   * @param hashicons A mapping between table columns and context attributes
   *                used to filter. The key must be the name of a table column,
   *                the value the name of an attribute.
   */
  @SuppressWarnings("unchecked")
  public TableFilter(IWDTable table, IWDAction filterAction,
          IWDNode sourceNode, Hashtable hashicons) {
    this(table, filterAction, sourceNode);
    if (hashicons != null) {
      Hashtable<String, String> names = (Hashtable<String, String>)hashicons;
      for (Map.Entry<String, String> entry : names.entrySet()) {
        setAttributeForColumn(entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * Specifies which context attribute is used to filter the given column. You
   * only have to call it if you don't want to filter by the primary binding of
   * the table cell editor.
   * 
   * @param columnName the name of the table column
   * @param attributePath the attribute name (or path). It always starts at the
   *                table's source node. You can reach child nodes by prepending
   *                it with the node name separated with a dot (e.g.
   *                <code>node1.node2.attribute</code>). If you set it to
   *                <code>null</code>, filtering is switched off for this
   *                attribute.
   */
  public void setAttributeForColumn(String columnName, String attributePath) {
    Column column = columnsByName.get(columnName);
    if (column == null) {
      return; // TODO throw exception?
    }
    column.setAttribute(attributePath);
  }

  /**
   * Set your own matcher for a column.
   * 
   * @param columnName the column name
   * @param useVisibleText if <code>true</code> the filter works on the
   *                visible text (<code>getAttributeAsText</code>),
   *                otherwise it works on the actual attribute value (<code>getAttributeValue</code>)
   * @param matcher the matcher
   */
  @SuppressWarnings("unchecked")
  public void setFilter(String columnName, boolean useVisibleText,
          Matcher<?> matcher) {
    Column column = columnsByName.get(columnName);
    if (column == null) {
      return; // TODO throw exception?
    }
    column.setFilter(useVisibleText, matcher);
  }

  /**
   * (Re)Applies the mapping filter to the NodeInfo so that the node filters.
   */
  public void filter() {
    activeFilters.clear();
    for (Column column : columnsByName.values()) {
      column.updateFilter();
    }
    sourceNode.getNodeInfo().setMappingFilter(mappingFilter);
  }

  private IWDNodeInfo getFilterNodeInfo() {
    if (filterNodeInfo == null) {
      IWDNodeInfo parentInfo = sourceNode.getNodeInfo().getParent();
      filterNodeInfo = parentInfo.addChild("__"
              + sourceNode.getNodeInfo().getName() + "_filter__", null, true,
              CMICardinality.ONE, CMICardinality.ONE, false, null);
    }
    return filterNodeInfo;
  }

  private static final String EQ = "=";

  private static final String NE = "#";

  private static final String RANGE = "~";

  /** the table's source node */
  private final IWDNode sourceNode;

  /** the known table columns arranged by column name */
  private final Map<String, Column> columnsByName = new HashMap<String, Column>();

  /** the active table columns (with a non-null filter value) */
  private final Map<String, Column> activeFilters = new HashMap<String, Column>();

  /** the filter node (only created if necessary) */
  private IWDNodeInfo filterNodeInfo;

  /** Inner class implementing the necessary Web Dynpro interfaces */
  private final MappingFilter mappingFilter = new MappingFilter();

  /**
   * A matcher that searches for the filterValue within the given value. In
   * order to be case-insensitive, both are converted to uppercase. (This is not
   * perfect, I know. I better solution is probably to create a case-insensitive
   * Pattern).
   */
  private static class SimpleStringMatcher implements Matcher<String>
  {
    private String pattern;

    private final Locale locale;

    public SimpleStringMatcher() {
      locale = WDResourceHandler.getCurrentSessionLocale();
    }

    public void setFilterValue(String filterValue) {
      pattern = filterValue.toUpperCase(locale);
    }

    public boolean matches(String value) {
      return pattern == null
              || (value != null && value.toUpperCase(locale).contains(pattern));
    }
  }

  /**
   * A matcher for booleans. It only takes an empty text, assuming that the
   * surrounding TableFilter already handled the '=' or '#'.
   */
  private class BooleanMatcher implements Matcher<Boolean>
  {
    public boolean matches(Boolean value) {
      return Boolean.TRUE.equals(value);
    }

    public void setFilterValue(String filterValue) {
      if (filterValue.length() > 0) {
        throw new WDNonFatalRuntimeException("A boolean filter accepts only ''='' or ''#''");
      }
    }
  }

  /**
   * A matcher that can filter Comparables. The filterValue can either be one
   * value (all row values must equal it then) or a range in the form
   * <code>min~max</code> (then a row matches if
   * <code>min <= rowvalue <= max</code>). <code>min~</code> and 
   * <code>~max</code> is also recognized.
   * <p>
   * The filter uses an <code>ISimpleType</code> to parse the filter string.
   * So it is limited to those classes that have a pre-defined ISimpleType
   * (basically all primitives, <code>java.sql.Date</code> and all subclasses of 
   * <code>java.lang.Number</code>).
   */
  private class RangeMatcher implements Matcher<Comparable<Object>>
  {
    private Comparable<Object> min;

    private Comparable<Object> max;

    private final ISimpleType type;

    public RangeMatcher(Class<?> clazz) {
      type = sourceNode.getNodeInfo().getContext().getController().getComponent()
              .getDictionaryBroker().getPredefinedSimpleType(clazz);
      if (type == null) {
        throw new IllegalStateException();
      }
    }

    public void setFilterValue(String pattern) {
      int separatorPos = pattern.indexOf(RANGE);
      if (separatorPos >= 0) {
        min = getValue(type, pattern.substring(0, separatorPos));
        max = getValue(type, pattern.substring(separatorPos + RANGE.length()));
      } else {
        min = max = getValue(type, pattern);
      }
    }

    public boolean matches(Comparable<Object> value) {
      return (min == null || compare(min, value) <= 0)
              && (max == null || compare(value, max) <= 0);
    }

    private Comparable<Object> getValue(ISimpleType type, String text) {
      text = text.trim();
      if (text.length() == 0) {
        return null;
      }
      try {
        @SuppressWarnings("unchecked")
        Comparable<Object> value = (Comparable<Object>)type.parse(text);
        return value;
      } catch (ParseException e) {
        // value is not parseable, so no filtering
        throw new WDNonFatalRuntimeException(e);
      }
    }

    private int compare(Comparable<Object> value1, Comparable<Object> value2) {
      if (value1 == null) {
        return value2 == null ? -1 : 0;
      }
      if (value2 == null) {
        return 1;
      }
      return value1.compareTo(value2);
    }
  }

  /**
   * A simple implementation to match a Core Component Type. It uses another
   * Matcher to compare against the content component.
   */
  private static class CctMatcher implements Matcher<ICctContainer>
  {
    private Matcher<Object> contentMatcher;

    private boolean active;

    @SuppressWarnings("unchecked")
    CctMatcher(Matcher<?> contentMatcher) {
      this.contentMatcher = (Matcher<Object>)contentMatcher;
    }

    public void setFilterValue(String filterValue) {
      if (filterValue == null) {
        active = false;
      } else {
        active = true;
        contentMatcher.setFilterValue(filterValue);
      }
    }

    public boolean matches(ICctContainer value) {
      return !active
              || (value != null && contentMatcher.matches(
                      value.getComponentValue(ICctContainer.CONTENT)));
    }
  }

  /**
   * Column holds all necessary information for a column of the table.
   */
  private class Column implements Matcher<IWDNodeElement>
  {
    /** The column */
    private final IWDTableColumn column;

    /**
     * The accessor used to retrieve the "column" value of a given element. May
     * be <code>null</code> if no valid binding could be found or if it was
     * explicitely disabled via {@link TableFilter#setAttributeForColumn(String, String)}.
     */
    private Accessor<Object> accessor;

    /**
     * The matcher used to filter out. It is <code>null</code> if no matcher
     * could be determined, in this case filtering is switched of by removing
     * the binding to the filterAttribute again.
     */
    private Matcher<Object> matcher;

    /** The value of the filter field which is currently being used */
    private String filterValue;

    /**
     * An accessor that can retrieve the filter value. Must be called with the
     * root NodeElement as starting point.
     */
    private Accessor<String> filterValueAccessor;

    /**
     * The binding attribute for the filter attribute. May be withdran from the
     * table if no appropriate Matcher or Accessor can be found.
     */
    private String filterBinding;

    /**
     * Whether to negate the matcher's result (when the filter value started
     * with '#')
     */
    private boolean negate;

    Column(IWDTableColumn column) {
      this.column = column;
      prepareFilterAttribute();
      setAttribute(getRelativeBindingOfPrimaryProperty(column));
    }

    /**
     * Declares an(other) attribute and determines an appropriate matcher. May
     * activate or deactivate filtering for this column.
     */
    void setAttribute(String attributeName) {
      accessor = attributeName != null ? 
              new ValueAccessor<Object>(attributeName) : null;
      prepareMatcher();
    }

    /**
     * 
     */
    void updateFilter() {
      IWDNodeElement rootElement = sourceNode.getContext().getRootNode().getCurrentElement();
      String currentFilterValue = filterValueAccessor.get(rootElement);
      String columnName = column.getId();
      if (currentFilterValue != null) {
        try {
          if (!currentFilterValue.equals(filterValue)) {
            setFilterValue(currentFilterValue);
          }
          activeFilters.put(column.getId(), this);
        } catch (WDNonFatalRuntimeException e) {
          IWDMessageManager messageManager = sourceNode.getContext().getController().getComponent().getMessageManager();
          messageManager.reportInvalidContextAttributeException(filterValueAccessor.getAttributePointer(rootElement), e.getMessage());
        }
      }
    }

    @SuppressWarnings("unchecked")
    void setFilter(boolean useText, Matcher<?> matcher) {
      this.accessor = (Accessor<Object>)(useText ? new TextAccessor(accessor)
              : new ValueAccessor<Object>(accessor));
      this.matcher = (Matcher<Object>)matcher;
      prepareMatcher();
    }

    public boolean matches(IWDNodeElement element) {
      return matcher == null
              || (negate ^ matcher.matches(accessor.get(element)));
    }

    /**
     * Sets a new filter value. Determines and splits off the negate flag and
     * passes the rest to the matcher.
     */
    public void setFilterValue(String filterValue) {
      negate = false;
      if (filterValue.startsWith(EQ)) {
        filterValue = filterValue.substring(EQ.length());
      } else if (filterValue.startsWith(NE)) {
        filterValue = filterValue.substring(NE.length());
        negate = true;
      }
      if (matcher != null) {
        matcher.setFilterValue(filterValue);
      }
    }

    /**
     * Searches or creates a filter attribute and adds an
     * CalculatedAttributeAccessor at it to be informed about changes in the
     * filter values.
     */
    private void prepareFilterAttribute() {
      if (filterValueAccessor == null) {
        if (column.bindingOfFilterValue() == null) {
          // no filter attribute -> create and bind one
          IWDAttributeInfo filterAttribute = 
                  getFilterNodeInfo().addAttribute(column.getId(), "string");
          column.bindFilterValue(filterAttribute);
        }
        filterBinding = column.bindingOfFilterValue();
        filterValueAccessor = new ValueAccessor<String>(filterBinding);
      }
    }

    /**
     * Prepares the matcher. It creates one if possible and necessary and
     * activates the filter attribute if a matcher exists.
     */
    @SuppressWarnings("unchecked")
    private void prepareMatcher() {
      if (accessor != null && matcher == null) {
        IDataType dataType = accessor.getDataType(sourceNode.getNodeInfo());
        matcher = (Matcher<Object>)createMatcher(dataType);
      }
      column.bindFilterValue(matcher != null ? filterBinding : null);
    }

    /**
     * Creates a matcher for the given data type.
     * 
     * @return the matcher or <code>null</code> if no generic matcher is known
     *         for that type
     */
    private Matcher<?> createMatcher(IDataType type) {
      if (type instanceof ICoreComponentType) {
        ICoreComponentType cct = (ICoreComponentType)type;
        IComponent content = cct.getComponent(ICoreComponentType.CONTENT);
        return new CctMatcher(createMatcher(content.getType()));
      }
      Class<?> clazz = type.getAssociatedClass();
      if (String.class.isAssignableFrom(clazz)) {
        return new SimpleStringMatcher();
      }
      if (clazz == boolean.class || clazz == Boolean.class) {
        return new BooleanMatcher();
      }
      if ((clazz.isPrimitive()) || Comparable.class.isAssignableFrom(clazz)) {
        return new RangeMatcher(clazz);
      }
      return null;
    }
  }

  /**
   * The internal filter class implementing the necessary Web Dynpro interfaces.
   * It is an IWDMappingFilter to do the actual filtering. It is an
   * IWDCalculatedAttributeAccessor in order to be informed about changes in the
   * filter values. (The filter attributes are declared as buffered calculated
   * attributes so that Web Dynpro stores the value <u>and</u> notifies the
   * accessor.)
   */
  private class MappingFilter implements IWDMappingFilter
  {
    /**
     * Here the filtering happens!
     */
    public boolean isVisible(IWDNodeElement element) {
      // check that the element matches all filters
      for (Column filter : activeFilters.values()) {
        if (!filter.matches(element)) {
          return false;
        }
      }
      return true;
    }
  }
}
