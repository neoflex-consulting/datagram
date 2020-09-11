import React, { Component } from 'react';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import PropTypes from 'prop-types';
import { copyIntoClipboard } from './../utils/clipboard.js';

class NfDataGrid extends Component {

    static propTypes = {
        columnDefs: PropTypes.array.isRequired,
        rowData: PropTypes.array
    }

    static defaultProps = {
        onCtrlA: null,
        onCtrlShiftA: null,
        headerSelection: false,
        onHeaderSelection: null
    }

    constructor(...args) {
        super(...args);
        this.grid = React.createRef();
        this.exportToCSV = this.exportToCSV.bind(this);
        this.handleKeyDown = this.handleKeyDown.bind(this);
    }

    handleKeyDown(event) {
        const { onCtrlA, onCtrlShiftA } = this.props
        const rowData = this.grid.current.api.getSelectedRows()
        const focusedCell = this.grid.current.api.getFocusedCell()
        const row = this.grid.current.api.getDisplayedRowAtIndex(focusedCell.rowIndex);

        let charCode = String.fromCharCode(event.which).toLowerCase()
            if (rowData.length > 0 && focusedCell) {
                const cellData = row.data[focusedCell.column.colId]
                if (event.ctrlKey && charCode === 'c') {
                    copyIntoClipboard(cellData)
                    event.preventDefault()
                }
                // For MAC
                if (event.metaKey && charCode === 'c') {
                    copyIntoClipboard(cellData)
                    event.preventDefault()
                }
            }
            if (this.props.onCtrlA) {
                if (event.ctrlKey && charCode === 'a') {
                    onCtrlA(event)
                    event.preventDefault()
                }
            }
            if (this.props.onCtrlShiftA) {
                if (event.ctrlKey && event.shiftKey && charCode === 'a') {
                    onCtrlShiftA(event)
                    event.preventDefault()
                }
            }
    }

    exportToCSV(name) {
        this.grid.current.api.exportDataAsCsv({ fileName: name })
    }

    render() {
        const { columnDefs, rowData, gridOptions, headerSelection } = this.props
        const TableHeaderSelection = (style) =>
            <div
                onClick={this.props.onHeaderSelection}
                style={{
                    width: '25px',
                    height: '25px',
                    position: 'absolute',
                    zIndex: 1,
                    left: '3px',
                    top: '0px',
                    cursor: 'pointer'
                }}
            >
                <svg width="10" height="10" xmlns="http://www.w3.org/2000/svg">
                    <g>
                        <rect x="-1" y="-1" width="12" height="12" id="canvas_background" fill="none" />
                        <g id="canvasGrid" display="none">
                            <rect id="svg_5" width="100%" height="100%" x="0" y="0" strokeWidth="0" fill="url(#gridpattern)" />
                        </g>
                    </g>
                    <g>
                        <line strokeLinecap="null" strokeLinejoin="null" id="svg_7" y2="8.152346" x2="3.718758" y1="5.152351" x1="0.843763" fillOpacity="null" strokeOpacity="null" strokeWidth="1.5" stroke="#cccccc" fill="none" />
                        <line stroke="#cccccc" transform="rotate(90.56033325195312 5.968754291534424,5.121101379394531) " strokeLinecap="null" strokeLinejoin="null" id="svg_8" y2="8.355427" x2="9.017037" y1="1.886777" x1="2.920472" fillOpacity="null" strokeOpacity="null" strokeWidth="1.5" fill="none" />
                    </g>
                </svg>
            </div>

        return (
            <div
                onKeyDown={this.handleKeyDown}
                style={{ boxSizing: 'border-box', height: '100%', width: '100%' }}
                className="ag-theme-balham"
            >
                {headerSelection && <TableHeaderSelection />}
                <AgGridReact
                    ref={this.grid}
                    columnDefs={columnDefs}
                    rowData={rowData}
                    enableColResize={true}
                    pivotHeaderHeight={true}
                    enableSorting={true}
                    sortingOrder={["desc", "asc", null]}
                    enableFilter={true}
                    gridAutoHeight={true}
                    {...gridOptions}
                />
            </div>
        )
    }
}

export default NfDataGrid;
