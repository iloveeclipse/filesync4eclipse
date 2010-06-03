/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.dialogs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;

/**
 * A list with a button bar.
 * Typical buttons are 'Add', 'Remove', 'Up' and 'Down'.
 * List model is independend of widget creation.
 * DialogFields controls are: Label, List and Composite containing buttons.
 */
public class ListDialogField<T> extends DialogField {

    protected TableViewer fTable;
    protected Control fTableControl;
    protected ILabelProvider fLabelProvider;
    protected ListViewerAdapter fListViewerAdapter;
    protected List<T> fElements;
    protected ViewerSorter fViewerSorter;

    protected String[] fButtonLabels;
    private Button[] fButtonControls;

    private boolean[] fButtonsEnabled;

    private int fRemoveButtonIndex;
    private final int fUpButtonIndex;
    private final int fDownButtonIndex;

    private Composite fButtonsControl;
    private ISelection fSelectionWhenEnabled;

    private final IListAdapter<T> fListAdapter;

    private final Object fParentElement;



    /**
     * Creates the <code>ListDialogField</code>.
     * @param adapter A listener for button invocation, selection changes. Can
     * be <code>null</code>.
     * @param buttonLabels The labels of all buttons: <code>null</code> is a valid array entry and
     * marks a separator.
     * @param lprovider The label provider to render the table entries
     */
    public ListDialogField(IListAdapter<T> adapter, String[] buttonLabels, ILabelProvider lprovider) {
        super();
        fListAdapter= adapter;

        fLabelProvider= lprovider;
        fListViewerAdapter= new ListViewerAdapter();
        fParentElement= this;

        fElements= new ArrayList<T>(10);

        fButtonLabels= buttonLabels;
        if (fButtonLabels != null) {
            int nButtons= fButtonLabels.length;
            fButtonsEnabled= new boolean[nButtons];
            for (int i= 0; i < nButtons; i++) {
                fButtonsEnabled[i]= true;
            }
        }

        fTable= null;
        fTableControl= null;
        fButtonsControl= null;

        fRemoveButtonIndex= -1;
        fUpButtonIndex= -1;
        fDownButtonIndex= -1;
    }

    /**
     * Sets the index of the 'remove' button in the button label array passed in the constructor.
     * The behaviour of the button marked as the 'remove' button will then be handled internally.
     * (enable state, button invocation behaviour)
     */
    public void setRemoveButtonIndex(int removeButtonIndex) {
        Assert.isTrue(removeButtonIndex < fButtonLabels.length);
        fRemoveButtonIndex= removeButtonIndex;
    }

    /**
     * Sets the viewerSorter.
     * @param viewerSorter The viewerSorter to set
     */
    public void setViewerSorter(ViewerSorter viewerSorter) {
        fViewerSorter= viewerSorter;
    }

    private void buttonPressed(int index) {
        if (!managedButtonPressed(index) && fListAdapter != null) {
            fListAdapter.customButtonPressed(this, index);
        }
    }

    /**
     * Checks if the button pressed is handled internally
     * @return Returns true if button has been handled.
     */
    protected boolean managedButtonPressed(int index) {
        if (index == fRemoveButtonIndex) {
            remove();
        } else if (index == fUpButtonIndex) {
            up();
            if (!fButtonControls[index].isEnabled() && fDownButtonIndex != -1) {
                fButtonControls[fDownButtonIndex].setFocus();
            }
        } else if (index == fDownButtonIndex) {
            down();
            if (!fButtonControls[index].isEnabled() && fUpButtonIndex != -1) {
                fButtonControls[fUpButtonIndex].setFocus();
            }
        } else {
            return false;
        }
        return true;
    }


    // ------ layout helpers

    @Override
    public Control[] doFillIntoGrid(Composite parent, int nColumns) {
        PixelConverter converter = new PixelConverter(parent);

        assertEnoughColumns(nColumns);

        Label label= getLabelControl(parent);
        GridData gd= gridDataForLabel(1);
        gd.verticalAlignment= GridData.BEGINNING;
        label.setLayoutData(gd);

        Control list= getListControl(parent);
        gd= new GridData();
        gd.horizontalAlignment= GridData.FILL;
        gd.grabExcessHorizontalSpace= false;
        gd.verticalAlignment= GridData.FILL;
        gd.grabExcessVerticalSpace= true;
        gd.horizontalSpan= nColumns - 2;
        gd.widthHint= converter.convertWidthInCharsToPixels(50);
        gd.heightHint= converter.convertHeightInCharsToPixels(6);

        list.setLayoutData(gd);

        Composite buttons= getButtonBox(parent);
        gd= new GridData();
        gd.horizontalAlignment= GridData.FILL;
        gd.grabExcessHorizontalSpace= false;
        gd.verticalAlignment= GridData.FILL;
        gd.grabExcessVerticalSpace= true;
        gd.horizontalSpan= 1;
        buttons.setLayoutData(gd);

        return new Control[] { label, list, buttons };
    }

    @Override
    public int getNumberOfControls() {
        return 3;
    }

    /**
     * Returns the list control. When called the first time, the control will be created.
     * @param parent The parent composite when called the first time, or <code>null</code>
     * after.
     */
    public Control getListControl(Composite parent) {
        if (fTableControl == null) {
            assertCompositeNotNull(parent);

            fTable= createTableViewer(parent);
            Table tableControl= fTable.getTable();

            fTableControl= tableControl;
            tableControl.setLayout(new TableLayout());

            fTable.getTable().addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    handleKeyPressed(e);
                }
            });

            //fTableControl.setLayout(tableLayout);

            fTable.setContentProvider(fListViewerAdapter);
            fTable.setLabelProvider(fLabelProvider);
            fTable.addSelectionChangedListener(fListViewerAdapter);
            fTable.addDoubleClickListener(fListViewerAdapter);

            fTable.setInput(fParentElement);

            if (fViewerSorter != null) {
                fTable.setSorter(fViewerSorter);
            }

            fTableControl.setEnabled(isEnabled());
            if (fSelectionWhenEnabled != null) {
                postSetSelection(fSelectionWhenEnabled);
            }
        }
        return fTableControl;
    }

    protected int getListStyle(){
        return SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL;
    }

    protected TableViewer createTableViewer(Composite parent) {
        Table table= new Table(parent, getListStyle());
        table.setFont(parent.getFont());
        return new TableViewer(table);
    }

    protected Button createButton(Composite parent, String label, SelectionListener listener) {
        Button button= new Button(parent, SWT.PUSH);
        button.setFont(parent.getFont());
        button.setText(label);
        button.addSelectionListener(listener);
        GridData gd= new GridData();
        gd.horizontalAlignment= GridData.FILL;
        gd.grabExcessHorizontalSpace= true;
        gd.verticalAlignment= GridData.BEGINNING;
        gd.widthHint = LayoutUtil.getButtonWidthHint(button);

        button.setLayoutData(gd);

        return button;
    }

    /**
     * Returns the composite containing the buttons. When called the first time, the control
     * will be created.
     * @param parent The parent composite when called the first time, or <code>null</code>
     * after.
     */
    public Composite getButtonBox(Composite parent) {
        if (fButtonsControl == null) {
            assertCompositeNotNull(parent);

            SelectionListener listener= new SelectionListener() {
                public void widgetDefaultSelected(SelectionEvent e) {
                    doButtonSelected(e);
                }
                public void widgetSelected(SelectionEvent e) {
                    doButtonSelected(e);
                }
            };

            Composite contents= new Composite(parent, SWT.NONE);
            contents.setFont(parent.getFont());
            GridLayout layout= new GridLayout();
            layout.marginWidth= 0;
            layout.marginHeight= 0;
            contents.setLayout(layout);

            if (fButtonLabels != null) {
                fButtonControls= new Button[fButtonLabels.length];
                for (int i= 0; i < fButtonLabels.length; i++) {
                    String currLabel= fButtonLabels[i];
                    if (currLabel != null) {
                        fButtonControls[i]= createButton(contents, currLabel, listener);
                        fButtonControls[i].setEnabled(isEnabled() && fButtonsEnabled[i]);
                    } else {
                        fButtonControls[i]= null;
                    }
                }
            }

            updateButtonState();
            fButtonsControl= contents;
        }

        return fButtonsControl;
    }

    protected void doButtonSelected(SelectionEvent e) {
        if (fButtonControls != null) {
            for (int i= 0; i < fButtonControls.length; i++) {
                if (e.widget == fButtonControls[i]) {
                    buttonPressed(i);
                    return;
                }
            }
        }
    }

    /**
     * Handles key events in the table viewer. Specifically
     * when the delete key is pressed.
     */
    protected void handleKeyPressed(KeyEvent event) {
        if (event.character == SWT.DEL && event.stateMask == 0) {
            if (fRemoveButtonIndex != -1 && isButtonEnabled(fTable.getSelection(), fRemoveButtonIndex)) {
                managedButtonPressed(fRemoveButtonIndex);
            }
        }
    }

    // ------ enable / disable management

    @Override
    public void dialogFieldChanged() {
        super.dialogFieldChanged();
        updateButtonState();
    }

    /*
     * Updates the enable state of the all buttons
     */
    protected void updateButtonState() {
        if (fButtonControls != null && isOkToUse(fTableControl)) {
            ISelection sel= fTable.getSelection();
            for (int i= 0; i < fButtonControls.length; i++) {
                Button button= fButtonControls[i];
                if (isOkToUse(button)) {
                    button.setEnabled(isButtonEnabled(sel, i));
                }
            }
        }
    }

    protected boolean getManagedButtonState(ISelection sel, int index) {
        if (index == fRemoveButtonIndex) {
            return !sel.isEmpty();
        } else if (index == fUpButtonIndex) {
            return !sel.isEmpty() && canMoveUp();
        } else if (index == fDownButtonIndex) {
            return !sel.isEmpty() && canMoveDown();
        }
        return true;
    }

    @Override
    protected void updateEnableState() {
        super.updateEnableState();

        boolean enabled= isEnabled();
        if (isOkToUse(fTableControl)) {
            if (!enabled) {
                fSelectionWhenEnabled= fTable.getSelection();
                selectElements(null);
            } else {
                selectElements(fSelectionWhenEnabled);
                fSelectionWhenEnabled= null;
            }
            fTableControl.setEnabled(enabled);
        }
        updateButtonState();
    }

    /**
     * Sets a button enabled or disabled.
     */
    public void enableButton(int index, boolean enable) {
        if (fButtonsEnabled != null && index < fButtonsEnabled.length) {
            fButtonsEnabled[index]= enable;
            updateButtonState();
        }
    }

    private boolean isButtonEnabled(ISelection sel, int index) {
        boolean extraState= getManagedButtonState(sel, index);
        return isEnabled() && extraState && fButtonsEnabled[index];
    }


    // ------ model access

    /**
     * Sets the elements shown in the list.
     */
    public void setElements(Collection<T> elements) {
        fElements= new ArrayList<T>(elements);
        if (isOkToUse(fTableControl)) {
            fTable.refresh();
        }
        dialogFieldChanged();
    }

    /**
     * Gets the elements shown in the list.
     * The list returned is a copy, so it can be modified by the user.
     */
    public List<T> getElements() {
        return new ArrayList<T>(fElements);
    }

    /**
     * Replaces an element.
     */
    public void replaceElement(T oldElement, T newElement) throws IllegalArgumentException {
        int idx= fElements.indexOf(oldElement);
        if (idx != -1) {
            fElements.set(idx, newElement);
            if (isOkToUse(fTableControl)) {
                List<T> selected= getSelectedElements();
                if (selected.remove(oldElement)) {
                    selected.add(newElement);
                }
                fTable.refresh();
                selectElements(new StructuredSelection(selected));
            }
            dialogFieldChanged();
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Adds an element at the end of the list.
     */
    public void addElement(T element) {
        addElement(element, fElements.size());
    }

    /**
     * Adds an element at a position.
     */
    public void addElement(T element, int index) {
        if (fElements.contains(element)) {
            return;
        }
        fElements.add(index, element);
        if (isOkToUse(fTableControl)) {
            fTable.refresh();
            fTable.setSelection(new StructuredSelection(element));
        }

        dialogFieldChanged();
    }

    /**
     * Removes elements from the list.
     */
    public void removeElements(List<T> elements) {
        if (elements.size() > 0) {
            fElements.removeAll(elements);
            if (isOkToUse(fTableControl)) {
                fTable.remove(elements.toArray());
            }
            dialogFieldChanged();
        }
    }

    public void selectElements(ISelection selection) {
        fSelectionWhenEnabled= selection;
        if (isOkToUse(fTableControl)) {
            fTable.setSelection(selection, true);
        }
    }

    public void selectFirstElement() {
        Object element= null;
        if (fViewerSorter != null) {
            Object[] arr= fElements.toArray();
            fViewerSorter.sort(fTable, arr);
            if (arr.length > 0) {
                element= arr[0];
            }
        } else {
            if (fElements.size() > 0) {
                element= fElements.get(0);
            }
        }
        if (element != null) {
            selectElements(new StructuredSelection(element));
        }
    }


    public void postSetSelection(final ISelection selection) {
        if (isOkToUse(fTableControl)) {
            Display d= fTableControl.getDisplay();
            d.asyncExec(new Runnable() {
                public void run() {
                    if (isOkToUse(fTableControl)) {
                        selectElements(selection);
                    }
                }
            });
        }
    }

    /**
     * Refreshes the table.
     */
    @Override
    public void refresh() {
        super.refresh();
        if (isOkToUse(fTableControl)) {
            fTable.refresh();
        }
    }

    // ------- list maintenance

    private List<T> moveUp(List<T> elements, List<T> move) {
        int nElements= elements.size();
        List<T> res= new ArrayList<T>(nElements);
        T floating= null;
        for (int i= 0; i < nElements; i++) {
            T curr= elements.get(i);
            if (move.contains(curr)) {
                res.add(curr);
            } else {
                if (floating != null) {
                    res.add(floating);
                }
                floating= curr;
            }
        }
        if (floating != null) {
            res.add(floating);
        }
        return res;
    }

    private void moveUp(List<T> toMoveUp) {
        if (toMoveUp.size() > 0) {
            setElements(moveUp(fElements, toMoveUp));
            fTable.reveal(toMoveUp.get(0));
        }
    }

    private void moveDown(List<T> toMoveDown) {
        if (toMoveDown.size() > 0) {
            setElements(reverse(moveUp(reverse(fElements), toMoveDown)));
            fTable.reveal(toMoveDown.get(toMoveDown.size() - 1));
        }
    }

    private List<T> reverse(List<T> p) {
        List<T> reverse= new ArrayList<T>(p.size());
        for (int i= p.size()-1; i >= 0; i--) {
            reverse.add(p.get(i));
        }
        return reverse;
    }


    private void remove() {
        removeElements(getSelectedElements());
    }

    private void up() {
        moveUp(getSelectedElements());
    }

    private void down() {
        moveDown(getSelectedElements());
    }

    private boolean canMoveUp() {
        if (isOkToUse(fTableControl)) {
            int[] indc= fTable.getTable().getSelectionIndices();
            for (int i= 0; i < indc.length; i++) {
                if (indc[i] != i) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canMoveDown() {
        if (isOkToUse(fTableControl)) {
            int[] indc= fTable.getTable().getSelectionIndices();
            int k= fElements.size() - 1;
            for (int i= indc.length - 1; i >= 0 ; i--, k--) {
                if (indc[i] != k) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the selected elements.
     */
    @SuppressWarnings("unchecked")
    public List<T> getSelectedElements() {
        List<T> result= new ArrayList<T>();
        if (isOkToUse(fTableControl)) {
            ISelection selection= fTable.getSelection();
            if (selection instanceof IStructuredSelection) {
                Iterator<?> iter= ((IStructuredSelection)selection).iterator();
                while (iter.hasNext()) {
                    result.add((T) iter.next());
                }
            }
        }
        return result;
    }

    // ------- ListViewerAdapter

    class ListViewerAdapter implements IStructuredContentProvider, ISelectionChangedListener, IDoubleClickListener {

        // ------- ITableContentProvider Interface ------------

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // will never happen
        }

        public void dispose() {
            // noop
        }

        public Object[] getElements(Object obj) {
            return fElements.toArray();
        }

        // ------- ISelectionChangedListener Interface ------------

        public void selectionChanged(SelectionChangedEvent event) {
            doListSelected(event);
        }

        public void doubleClick(DoubleClickEvent event) {
            doDoubleClick(event);
        }

    }


    protected void doListSelected(SelectionChangedEvent event) {
        updateButtonState();
        if (fListAdapter != null) {
            fListAdapter.selectionChanged(this);
        }
    }

    protected void doDoubleClick(DoubleClickEvent event) {
        if (fListAdapter != null) {
            fListAdapter.doubleClicked(this);
        }
    }

}
