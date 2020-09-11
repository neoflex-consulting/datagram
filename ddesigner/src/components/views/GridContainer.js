import React, {Component, Fragment} from 'react';
import {translate} from 'react-i18next';
import {Checkbox, Modal, Tooltip, Icon, Menu, Button, Avatar} from 'antd';
import resource from '../../Resource';
import {getClassDef} from './../../model'
import _ from 'lodash'
import DisplayList from './DisplayList'

const ButtonGroup = Button.Group;

class GridContainer extends Component {

    constructor(...args) {
        super(...args);
        let cols = _.get(getClassDef(this.props.activeObject.name), 'columns', [
            {
                Header: "Name",
                accessor: "name",
                indexKey: "name",
                sortable: true,
                show: true
            }]).map(c => {
            const field = getClassDef(this.props.activeObject.name).fields.find(f => f.name === c.accessor)
            const expCol = {
                Cell: (row) =>
                    (
                        <div style={{"marginLeft": "50%"}}>
                            <Checkbox checked={_.get(row.original, field.name, false)} disabled/>
                        </div>
                    )
            }
            return field && field.type === "boolean" ? {...c, ...expCol} : c
        })
        this.state = {
            objectButtons: [
                {
                    id: 'refresh', tooltip: this.props.t("refresh"), icon: 'images/icon-core/refresh-modern.svg', onClick: ()=>{
                        this.refresh()
                    }
                }
            ],
            columns: cols
        }
        this.componentButtonsList = {}
    }

    refresh() {
        if (this.props.activeObject) {
            this.props.onSelectObject(this.props.activeObject)
            this.loadObjectList(this.props.activeObject)
        }
    }

    getLinkedClasses(object) {
        return _.get(getClassDef(object._type_), "linkedClasses") || []
    }

    onButtonClick(val) {
        if (val.keyPath[val.keyPath.length - 1] === "new") {
            this.props.onSelectObject({_type_: val.key, name: "", e_id: undefined})
        }
        else {
            const btn = this.state.objectButtons.concat(this.componentButtonsList[this.getActiveComponentKey()] || []).filter((btn) => {
                return btn.id === val.key
            })[0]
            if (btn.onClick) {
                btn.onClick()
            }
        }
    }

    componentButtons(key, buttons) {
        this.componentButtonsList[key] = buttons
    }

    getActiveComponentKey() {
        return this.state.activeComponent ? this.state.activeComponent : (this.state.classDef ? (this.state.classDef.views ? this.state.classDef.views[0] : undefined) : undefined)
    }

    render() {
        const {t} = this.props
        const idAndType = [{"Header": "e_id","accessor": "e_id","indexKey": "e_id","sortable": true,"show": false},
                           {"Header": "_type_","accessor": "_type_","indexKey": "_type_","sortable": true,"show": false}]
        const col = _.concat(this.state.columns, idAndType)
        const readOnlyMode = _.get(getClassDef(this.props.activeObject.name), 'gridReadOnly')

        let activeComponent = this.getActiveComponentKey()
        return (
            <div>
                <Menu mode="horizontal" onClick={(val) => {
                    this.onButtonClick(val)
                }} selectable={false}>
                    {!readOnlyMode && <Menu.SubMenu key={"new"} title={<Tooltip placement="left" title={t("new")}>
                        <Avatar className='avatar-button-tool-panel' src='images/icon-core/plus-modern.svg'/>
                    </Tooltip>}>
                        {getClassDef(this.props.activeObject.name).successors.map(embeddedType => <Menu.Item
                            key={embeddedType}>{t(`${embeddedType}.caption`, { ns: 'classes' })}</Menu.Item>)}
                    </Menu.SubMenu>}
                    {(this.state.objectButtons || []).concat(this.componentButtonsList[activeComponent] || []).map((b) => {
                        return (
                            <Menu.Item key={b.id}>
                                <Tooltip placement="bottom" title={b.tooltip}>
                                    <Avatar className="avatar-button-tool-panel" src={b.icon}/>
                                </Tooltip>
                            </Menu.Item>
                        )
                    })}
                </Menu>
                <div>
                    <DisplayList
                        list={this.state.list}
                        storageId={"dd_" + this.props.activeObject.name}
                        columns={col}
                        controlColumn={{
                            Header: '',
                            accessor: 'e_id',
                            Cell: row => (
                                <ButtonGroup className={!readOnlyMode ? "pull-right" : "centre"}>
                                    <Tooltip placement="top" title={t("edit")}>
                                        <Button type="dashed" size="small" placement="" onClick={() => {
                                            const {_type_, e_id, name} = row.original
                                            this.props.onSelectObject({_type_, e_id, name})
                                        }}><Icon type="edit"/></Button>
                                    </Tooltip>
                                    {!readOnlyMode && <Fragment><Tooltip placement="top" title={t("copy")}>
                                        <Button type="dashed" size="small" placement="" onClick={() => {
                                            const newName = prompt(t('copyof'), `Copy_of_${row.original.name}`)
                                            if (newName) {
                                                resource.copyEntity(row.original._type_, row.original.e_id, newName).then(entity => {
                                                    const {_type_, e_id, name} = entity
                                                    this.props.onSelectObject({_type_, e_id, name})
                                                })
                                            }
                                        }}><Icon type="copy"/></Button>
                                    </Tooltip>
                                    <Tooltip placement="top" title={t("delete")}>
                                        <Button type="dashed" size="small" placement="" onClick={()=>{
                                            Modal.confirm({
                                                content: t("confirmdelete"),
                                                okText: t("delete"),
                                                cancelText: t("cancel"),
                                                onOk: ()=>{
                                                    resource.deleteEntity(row.original).then(entity => {
                                                        this.refresh()
                                                    })
                                                }
                                            })
                                        }}><Icon type="delete"/></Button>
                                    </Tooltip></Fragment>}
                                </ButtonGroup>
                            ),
                            filterable: false,
                            sortable: false,
                            resizable: false,
                            width: 120
                        }}

                    />
                </div>
            </div>)
    }

    loadObjectList(object) {
        var classDef = getClassDef(object._type_)
        this.setState({classDef})
        if (classDef.linkedClasses && classDef.linkedClasses[0].name === "Objects") {
            resource.getList(object.name)
                .then(data => {
                    this.setState({list: data})
                })
        }
    }


    componentDidMount() {
        if (this.props.activeObject) {
            this.loadObjectList(this.props.activeObject)
        }
    }

    componentWillReceiveProps(nextProps) {
        if (!_.isEqual(nextProps.activeObject, this.props.activeObject)) {
            if (nextProps.activeObject) {
                this.loadObjectList(nextProps.activeObject)
            }
        }
    }
}

export default translate()(GridContainer);
