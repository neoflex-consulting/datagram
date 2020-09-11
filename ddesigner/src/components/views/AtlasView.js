import React, {Component} from 'react';
import {translate} from "react-i18next";
import resource from "../../Resource";
import {Row, Button} from 'antd';
import JSONView  from './JSONView';
import update from 'immutability-helper';

class AtlasView extends Component {

    constructor(...args) {
        super(...args);
        this.state = {atlasSchemes: [], projects: []}
    }


    propsChanged(props) {
        resource.query("/api/teneo/select/select s from rt.AtlasScheme s where s.atlas.e_id=" + props.entity.e_id).then(
            result => {
                this.setState({atlasSchemes: result}, ()=>{
                    this.state.atlasSchemes.forEach((sa, idx)=>{
                        resource.query("/api/teneo/select/select s from rt.SoftwareSystem s where s.project.e_id=" + this.props.entity.project.e_id + " and s.scheme.e_id=" + sa.scheme.e_id).then(
                            result => {
                                result.forEach(ss => {
                                    resource.query("/api/teneo/select/select d from rt.Deployment d where d.e_id=" + ss.defaultDeployment.e_id).then(
                                        result => {
                                            let n = update(this.state, {atlasSchemes: {[idx]: {$merge: {deployment: result[0]}}}})
                                            this.setState({atlasSchemes: n.atlasSchemes}, this.loadSchemeTables(n.atlasSchemes[idx], idx))
                                            this.getSchemeQName(sa.scheme.name, result[0], idx)
                                    })
                                })
                            })
                        })
                    })
                })
        resource.query("/api/teneo/select/select p from rt.AtlasProject p where p.atlas.e_id=" + props.entity.e_id).then(
            result => {
                this.setState({projects: result.map(project=>{
                    delete project.atlas;
                    project.project = {
                        _type_: project.project._type_,
                        name: project.project.name,
                        e_id: project.project.e_id
                        }
                    return project;
                })})
            })
    }

    componentDidMount() {
        this.propsChanged(this.props)
    }

    componentWillReceiveProps(nextProps) {
        if (nextProps.entity.name !== this.props.entity.name) {
            this.propsChanged(nextProps)
        }
    }

    getSchemeQName(sname, deployment, idx) {
        resource.callStatic("rt.AtlasScheme", "initConnectionData", {schemeName: sname}).then(result =>
            {
                resource.callStatic("rt.AtlasScheme", "buildConnectionDataApi",
                    {connectionData: JSON.stringify(result), deployment: JSON.stringify(deployment)}).then(result =>
                        {
                            resource.callStatic("utils.AtlasEntity", "prepareQualifiedNameApi",{
                                connectionData: JSON.stringify(result), elements: JSON.stringify([sname])
                            }).then(result =>{
                                    var atlasScheme = this.state.atlasSchemes.find(sa=>sa.scheme && sa.scheme.name === sname)
                                    if(atlasScheme) {
                                        let n = update(this.state, {atlasSchemes: {[idx]: {$merge: {qualifiedName: result.result}}}})
                                        this.setState({atlasSchemes: n.atlasSchemes})
                                    }
                                }
                            )
                        })

            })
    }

    loadTablesQNames(atlasScheme, idx){
        let deployment = atlasScheme.deployment
        if(deployment) {
            resource.callStatic("rt.AtlasScheme", "initConnectionData", {schemeName: atlasScheme.scheme.name}).then(result =>
                {
                    resource.callStatic("rt.AtlasScheme", "buildConnectionDataApi",
                        {connectionData: JSON.stringify(result), deployment: JSON.stringify(deployment)}).then(result =>{
                                resource.callStatic("utils.AtlasEntity", "prepareTablesQualifiedNamesApi", {
                                    connectionData: JSON.stringify(result), elements: JSON.stringify(atlasScheme.tables)
                                }).then(result => {
                                    let n = update(this.state, {atlasSchemes: {[idx]: {$merge: {tables: result.result}}}})
                                    this.setState({atlasSchemes: n.atlasSchemes})
                                })
                        })
                })
        }
    }

    getByQName(typeName, qName, callback) {
        resource.callStatic("rt.AtlasScheme", "searchByTypeNameApi",
            {atlas: JSON.stringify(this.props.entity), qname: qName, typeName: typeName}).then(result =>{
                if(callback){
                    callback(result.result)
                }
            })
    }

    loadSchemeTables(atlasScheme, idx) {
        resource.query("/api/teneo/select/select t.name from rel.Scheme s join s.tables t where s.e_id=" + atlasScheme.scheme.e_id).then(
            result=>{
                atlasScheme.tables = result
                let n = update(this.state, {atlasSchemes: {[idx]: {$merge: {tables: result}}}})
                this.setState({atlasSchemes: n.atlasSchemes}, this.loadTablesQNames(atlasScheme, idx))
            }
        )
    }

    getTableQName(tname, atlasScheme) {
        if(!this.state["tablesQualifiedNames" + atlasScheme.scheme.e_id]) {
            return undefined
        } else {
            return this.state["tablesQualifiedNames" + atlasScheme.scheme.e_id][tname]
        }
    }

    getTName(tname, atlasScheme) {
        if(!this.state["tablesQualifiedNames" + atlasScheme.scheme.e_id]) {
            return tname
        } else {
            return <span>{tname} <Button onClick={()=>this.getByQName("rdbms_table", this.getTableQName(tname, atlasScheme), (entities)=>this.openInAtlas(entities[0]))}>{this.getTableQName(tname, atlasScheme)}</Button></span>
        }
    }

    openInAtlas(atlasEntity) {
        window.open(this.props.entity.http + "/index.html#!/detailPage/" + atlasEntity.guid, '_blank')
    }

    render() {
        return (
            <div>
                <Row >
                    <JSONView entity={this.state} />
                </Row>
            </div>
        )
    }
}

export default translate()(AtlasView);
