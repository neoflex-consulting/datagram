import React, { Component } from 'react';
import { translate } from 'react-i18next'
import { Button, Tooltip, Avatar } from 'antd';
import resource from "./../../Resource";
import PropTypes from 'prop-types';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import AceEditor from 'react-ace';
import 'brace/mode/scala';
import 'brace/theme/tomorrow';
import 'brace/ext/searchbox';
import { classExtension } from '../classExtension.js';

class Validation extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.tableGrid = React.createRef();
        this.state = {
            query: null
        }
    }

    tableOnSelect() {
        const { context } = this.props
        var selectedRow = this.refs.tableGrid.api.getSelectedRows()
        if (selectedRow[0] && context.selectNodeInDesigner) {
            const entity = classExtension.nodes(context.entity).find(n => n.e_id === selectedRow[0].e_id)
            context.selectNodeInDesigner(entity)
        }
    }

    createTable() {
        const { query } = this.state
        const data = query.problems.map(p => ({ context: p.context, constraint: p.constraint, isCritique: p.isCritique, message: p.message, e_id: p.e_id, _type_: p._type_ }))
        const columns = [{ headerName: 'Context', field: 'context', sortingOrder: ["asc", "desc"] },
        { headerName: 'Constraint', field: 'constraint', sortingOrder: ["asc", "desc"] },
        { headerName: 'isCritique', field: 'isCritique', sortingOrder: ["asc", "desc"] },
        { headerName: 'Message', field: 'message', sortingOrder: ["asc", "desc"], cellStyle: {backgroundColor: '#ff8f79'} }]
        return (
            <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                <AgGridReact
                    ref={'tableGrid'}
                    columnDefs={columns}
                    rowData={data}
                    domLayout={'autoHeight'}
                    enableColResize={'true'}
                    pivotHeaderHeight={'true'}
                    enableSorting={true}
                    sortingOrder={["desc", "asc", null]}
                    enableFilter={true}
                    rowSelection={'single'}
                    onSelectionChanged={(test) => {
                        this.tableOnSelect()
                    }}
                />
            </div>
        )
    }

    createTextField() {
        return (
            <AceEditor
                mode={'scala'}
                width={''}
                height={'10vh'}
                theme={'tomorrow'}
                fontSize={15}
                editorProps={{ $blockScrolling: Infinity }}
                value={'Ok!'}
                showPrintMargin={false}
                showGutter={false}
                focus={false}
                readOnly={true}
                minLines={5}
                highlightActiveLine={false}
            />
        )
    }

    validationButtonOnClick() {
        if (this.props.activeObject) {
            resource.call(this.props.activeObject, 'validate').then(json => {
                this.setState({ query: json })
            })
        }
    }

    render() {
        const { query } = this.state
        const { t } = this.props
        return (
            <div>
                <Tooltip placement="bottom" title={t('validate')}>
                    <Button style={{ marginLeft: "5px", marginTop: "5px", marginBottom: "5px" }} onClick={() => this.validationButtonOnClick()}>
                        <Avatar src="images/icon-core/check-modern.svg" size={"small"} />
                    </Button>
                </Tooltip>
                {query ?
                    query.result ? this.createTextField() : this.createTable()
                    :
                    null}
            </div>
        )

    }
}

export default translate()(Validation);
