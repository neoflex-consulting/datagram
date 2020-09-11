import React, { Component } from 'react';
import { Menu, Icon, Input, Avatar, Dropdown, Button } from 'antd';
import { translate } from "react-i18next";
import _ from 'lodash'
import { getClassDef } from './../model'
import resource from '../Resource';
import reactStringReplace from 'react-string-replace';
import { getIcon } from './../utils/meta'

class LeftPanel extends Component {
    constructor(...args) {
        super(...args);
        this.state = {
            linkedClasses: [],
            openKeys: [],
            activeObject: {}
        }
    }

    getLinkedClasses(object) {
        return _.get(getClassDef(object._type_), "linkedClasses") || []
    }

    findLinkedObjects(root, link, cb) {
        const clean = (inst) => {
            const { _type_, e_id, name } = inst
            return _.omitBy({ _type_, e_id, name }, _.isUndefined)
        }
        if (link.backReference || link.query) {
            if (root.e_id) {
                let query = undefined
                if (link.query) {
                    // eslint-disable-next-line no-template-curly-in-string
                    query = link.query.replace("${e_id}", root.e_id)
                }
                else if (link.backReference) {
                    query = `select type(t), t.e_id, t.name from ${link._type_} t where t.${link.backReference}.e_id = ${root.e_id} order by t.name`
                }
                resource.query(`/api/teneo/select/${query}`)
                    .then(list => list.map(row => _.zipObject(['_type_', 'e_id', 'name'], row)))
                    .then(data => cb(data))
            }
            else {
                cb([])
            }
        } else if (link.attribute) {
            cb(getClassDef(link._type_).instances.filter((i) => {
                const object = getClassDef(root._type_).instances.find((c) => {
                    return c.name === root.name
                })
                return object[link.attribute].includes(i.name)
            }).map(inst => clean(inst)))
        } else if (link.name === "Objects") {
            const getClass = getClassDef(root.name)
            if (getClass.query) {
                resource.query(`/api/teneo/select/${getClass.query}`)
                    .then(list => list.map(row => _.zipObject(['_type_', 'e_id', 'name'], row)))
                    .then(data => cb(data))
            }
            else {
                resource.getSimpleSelect(root.name, ["name"]).then(data => cb(_.sortBy(data, (item) => item.name.toLowerCase())))
            }
        } else {
            cb(getClassDef(link._type_).instances.map(inst => clean(inst)))
        }
    }

    onOpenChange = (openKeys) => {
        this.setState({
            openKeys: openKeys.filter((item) => {
                return this.state.openKeys.indexOf(item) < 0
            })
        })
    }

    openKeys() {
        return (this.props.searchStr && this.props.searchStr !== "") ?
            this.state.linkedClasses.map(([link, linkedObjects]) => {
                return link.name
            }) : this.state.openKeys
    }

    showSearch(text) {
        return (this.props.searchStr && this.props.searchStr !== "") ? (text ?
            reactStringReplace(text, this.props.searchStr, (match, i) => (
                <span key={i} style={{ color: 'red' }}>{match}</span>
            )) : text) : text
    }

    createLinkedObject(link) {
        const { t } = this.props
        if (!link.backReference) {
            return null
        }
        const createMenu = (<Menu onClick={e => {
            e.domEvent.stopPropagation()
            this.props.onSelectObject({ _type_: e.key, name: "", [link.backReference]: this.props.activeObject })
        }}>
            {getClassDef(link._type_).successors.map(embeddedType => <Menu.Item
                key={embeddedType}>{t(`${embeddedType}.caption`, { ns: 'classes' })}</Menu.Item>)}
        </Menu>)
        return <Dropdown overlay={createMenu}>
            <Button className="left-sider-menu-button" type="dashed" size="small" placement="" onClick={e => {
                e.stopPropagation()
            }}>
                <Avatar className='avatar-add' src='images/icon-core/file-add.svg' />
            </Button>
        </Dropdown>
    }

    render() {
        const { t, onSelectObject } = this.props
        return (<div>
            {!this.props.collapsed &&
                <div className="search-box">
                    <Input theme={"dark"}
                        prefix={<Icon type="search" />}
                        placeholder={t("search")}
                        onChange={(e) => this.props.handleSearchChange(e)} />
                </div>}
            <Menu mode="inline"
                className="left-panel-menu"
                onOpenChange={this.onOpenChange}
                openKeys={this.openKeys()}
                selectable={false}>
                {this.state.linkedClasses.sort((a, b) => a[0].name.localeCompare(b[0].name))
                    .map(([link, linkedObjects]) => {
                        return (
                            <Menu.ItemGroup
                                title={<span>
                                    <Avatar className="avatar-menu" size="small" src='images/icon-core/mail.svg' />
                                    <span>{t((link.name) + '.caption', { ns: ['references'] })}</span>{this.createLinkedObject(link)}</span>}
                                key={link.name}>
                                {linkedObjects.filter((item) => {
                                    return this.props.searchStr === "" || item.name.toUpperCase().includes(this.props.searchStr.toUpperCase())
                                }).map((item) => {
                                    var name = !!item.e_id ? item.name : (
                                        item._type_ === "ecore.EClass" ? t((item.name) + '.caption', { ns: ['classes'] }) : t((item.name) + '.caption', { ns: ['modules'] })
                                    )
                                    return (<Menu.Item key={item.e_id + name}>
                                        <div onClick={() => {
                                            onSelectObject(item)
                                        }}>
                                            <Avatar src={getIcon(item)} size={"small"} />{this.showSearch(name)}
                                        </div>
                                    </Menu.Item>
                                    )
                                })}
                            </Menu.ItemGroup>
                        )
                    })}
            </Menu>
        </div>)
    }

    setActiveObject(activeObject) {
        if (activeObject) {
            let openKeys, linkedClasses = []
            if(this.getLinkedClasses(activeObject).length === 0 && this.state.linkedClasses.length > 0){
                this.setState({
                    openKeys: [],
                    linkedClasses: []
                })
            }
            this.getLinkedClasses(activeObject).forEach(link => {
                this.findLinkedObjects(activeObject, link, linkedObjects => {
                    openKeys = this.state.openKeys.length === 0 ? [link.name] : this.state.openKeys
                    linkedClasses.push([link, linkedObjects].sort((a, b) => a[0] > b[0]))
                    this.setState({
                        openKeys: openKeys,
                        linkedClasses: linkedClasses
                    })
                })
            })
        }
    }

    componentDidMount() {
        const { activeObject } = this.props
        this.setActiveObject(activeObject)
    }

    componentDidUpdate(prevProps, prevState) {
        if (this.props.activeObject !== prevProps.activeObject) {
            this.setActiveObject(this.props.activeObject)
        }
    }
   
}

export default translate()(LeftPanel);
