import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap/dist/css/bootstrap-theme.css'
import 'react-table/react-table.css'
import React, { Component } from 'react';
import {Modal, OverlayTrigger, Tooltip, Glyphicon, Button, ButtonGroup} from 'react-bootstrap'
import PropTypes from 'prop-types'
import { translate } from 'react-i18next'
import ReactTable from 'react-table'
import _ from 'lodash'
import Select from 'react-select'

class OptsTable extends Component {
    static propTypes = {
        columns: PropTypes.array.isRequired,
        data: PropTypes.array.isRequired,
        controlColumn: PropTypes.object.isRequired,
        storageId: PropTypes.string
    }
    constructor() {
        super()
        this.state = {showOptions: false, selected: [], columns: []}
    }

    load(storageId, defFunc) {
        if (storageId) {
            const value = localStorage.getItem(storageId)
            if (value) {
                return JSON.parse(value)
            }
        }
        return defFunc()
    }

    store(storageId, value) {
        if (storageId) {
            localStorage.setItem(storageId, JSON.stringify(value))
        }
    }

    componentDidMount() {
        let {columns, storageId} = this.props
        const selected = this.load(storageId, ()=>columns.filter(c=>c.show).map(c=>({value: c.accessor, label: c.Header})))
        this.setState({selected, columns})
    }

    componentWillUnmount() {
        let {storageId} = this.props
        this.store(storageId, this.state.selected)
    }

    componentWillReceiveProps(nextProps) {
        const {columns} = nextProps
        this.setState({columns})
    }

    getColumns() {
        return [
            ...this.state.selected.map(c=>_.merge({}, this.state.columns.find(cb=>c.value === cb.accessor), {show: true})),
            ...this.state.columns.filter(c=>this.state.selected.findIndex(sc=>sc.value === c.accessor) < 0).map(c=>_.merge({}, c, {show: false}))
        ]
    }

    getAllColumns() {
        const {t} = this.props
        return [
            ...this.getColumns(),
            _.merge({}, this.props.controlColumn, {Header: (props) => (
                <ButtonGroup className="pull-right">
                    <OverlayTrigger placement="top" overlay={<Tooltip id="columns">{t("columns")}</Tooltip>}>
                        <Button bsSize="xsmall" id="columns" onClick={e=>this.setState({showOptions: !this.state.showOptions})}><Glyphicon glyph="cog"/></Button>
                    </OverlayTrigger>
                </ButtonGroup>
            )})
        ]
    }

    render() {
        const {data, pageSize, t, SubComponent} = this.props
        return (
            <div>
                <Modal show={this.state.showOptions} onHide={()=>this.setState({showOptions:false})}>
                    <Modal.Header closeButton>
                        <Modal.Title>{t('columns')}</Modal.Title>
                    </Modal.Header>
                    <Modal.Body>
                        <Select
                            name="columns"
                            multi={true}
                            value={this.state.selected}
                            onChange={(selected)=>this.setState({selected})}
                            options={_.sortBy(this.getColumns().map(c=>({value: c.accessor, label: c.Header})), (o)=>_.get(o, 'label', '').toLowerCase())}
                        />
                    </Modal.Body>
                </Modal>
                <ReactTable className='-striped -highlight'
                            collapseOnDataChange={false}
                            defaultPageSize={pageSize || 10}
                            filterable={true}
                            defaultFilterMethod={(filter, row) => (_.get(row, filter.id, '').toString().toLowerCase().includes(filter.value.toLowerCase()))}
                            data={data}
                            previousText={t('previous')}
                            nextText={t('next')}
                            loadingText={t('loading')}
                            noDataText={t('nodata')}
                            pageText={t('page')}
                            ofText={t('of')}
                            rowsText={t('rows')}
                            columns={this.getAllColumns()}
                      SubComponent={SubComponent}
                />
            </div>
        )
    }

}

export default translate()(OptsTable);
