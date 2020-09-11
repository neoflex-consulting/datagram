import React, {Component} from 'react';
import {translate} from "react-i18next";
import _ from 'lodash'
import { Form, Tooltip, Avatar, Button, Divider } from 'antd';
import 'brace/mode/json';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import AceEditor from "react-ace";
import resource from '../../Resource';
import {getTypeField} from '../../model';
import {findNonContainedReferences, getPaths, simplifyNonContainedReferences, restoreReferences} from "../../utils/meta";

class JSONView extends Component {

    constructor(...args) {
        super(...args);
        this.state = {}
    }

    static getDerivedStateFromProps(nextProps, prevState) {
        const newState = {}
        if (prevState && nextProps && !_.isEqual(prevState.entity, nextProps.entity)) {
            newState.entity = nextProps.entity
            newState.entityChanged = true
        }
        if (Object.keys(newState).length > 0) {
            return {...prevState, ...newState}
        }
        return null
    }

    getJson(original) {
        const entity = _.cloneDeepWith(original, (value, key, object) => {
            return undefined
        })
        const paths = new Map()
        getPaths(entity, paths, "")
        this.ncrefs = findNonContainedReferences(entity, "", paths, [])
        simplifyNonContainedReferences(entity, this.ncrefs)
        const json = JSON.stringify(entity, 0, 4)
        return json
    }

    componentDidMount() {
        this.setState({json: this.getJson(this.state.entity)})
    }

    componentDidUpdate(prevProps, prevState) {
        if (this.state.entityChanged) {
            this.setState({json: this.getJson(this.state.entity), entityChanged: undefined})
        }
    }

    save() {
        try {
            let entity = JSON.parse(this.state.json)
            restoreReferences(entity, this.ncrefs)
            this.props.updateEntity(entity)
        }
        catch (e) {
            console.log(e.toString())
            resource.logError(e.message)
        }
    }

    format() {
        try {
            let entity = JSON.parse(this.state.json)
            this.setState({json: JSON.stringify(entity, 0, 4)})
        }
        catch (e) {
            console.log(e.toString())
        }
    }


    clean() {
        function cloneAndClean(value) {
            if (!value) return value
            if (_.isPlainObject(value) && !!value._type_) {
                const {_type_} = value
                const cloned = {_type_}
                for (let key of Object.keys(value)) {
                    const field = getTypeField(_type_, key)
                    if (field) {
                        const prop = value[key]
                        if (field.isContained) {
                            if (field.isArray) {
                                cloned[key] = prop.map(e=>cloneAndClean(_.omit(e, ['e_id'])))
                            }
                            else {
                                cloned[key] = cloneAndClean(_.omit(prop, ['e_id']))
                            }
                        }
                        else { //
                            cloned[key] = _.cloneDeep(prop)
                        }
                    }
                }
                return cloned
            }
            return value
        }
        try {
            let entity = cloneAndClean(JSON.parse(this.state.json))
            this.setState({json: JSON.stringify(entity, 0, 4)})
        }
        catch (e) {
            console.log(e.toString())
        }
    }

    load() {
        this.setState({entity: this.props.entity, json: this.getJson(this.props.entity)})
    }

    render() {
        const { t } = this.props
        return (
            <div style={{ overflow: 'hidden', display: 'flex', flexDirection: 'column', height: 'calc(100vh - 150px)' }}>
                <Form layout={"inline"}>
                    <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                        <Tooltip placement="top" title={t("load")}>
                            <Button id="load" shape="circle" style={{ border: 0 }} onClick={() => {
                                this.load()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/upload-modern.svg"} />
                            </Button>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                        <Tooltip placement="top" title={t("format")}>
                            <Button id="format" shape="circle" style={{ border: 0 }} onClick={() => {
                                this.format()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/tree.svg"} />
                            </Button>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                        <Tooltip placement="top" title={t("clean")}>
                            <Button id="format" shape="circle" style={{ border: 0 }} onClick={() => {
                                this.clean()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/new-ind.svg"} />
                            </Button>
                        </Tooltip>
                    </Form.Item>
                    <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                        <Tooltip placement="top" title={t("save")}>
                            <Button id="save" shape="circle" style={{ border: 0 }} onClick={() => {
                                this.save()
                            }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/save-modern.svg"} />
                            </Button>
                        </Tooltip>
                    </Form.Item>
                </Form>
                <Divider style={{ marginTop: 0, marginBottom: 0 }} />
                <AceEditor
                    mode={'json'}
                    width={''}
                    height={'100%'}
                    theme={'sqlserver'}
                    fontSize={14}
                    editorProps={{$blockScrolling: false}}
                    value={this.state.json}
                    onChange={json => {
                        this.setState({json})
                    }}
                    showPrintMargin={false}
                    debounceChangePeriod={500}
                />
            </div>
        )
    }
}

export default translate()(JSONView);
