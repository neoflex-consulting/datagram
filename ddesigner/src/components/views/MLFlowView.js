import React, {Component} from 'react';
import {translate} from "react-i18next";
import resource from "../../Resource";
import ReactTable from 'react-table'

class MLFlowView extends Component {

    constructor(...args) {
        super(...args);
        this.state = {experiments: [], runs: [], expanded : {}}
    }

    propsChanged(props) {
        console.log("props changed")
        resource.callStatic("rt.MLFlowServer", "listExperiments", {entity: JSON.stringify(this.props.entity)}).then(
            result => {
                this.setState({
                    experiments: result
                }, () => console.log("this.state.experiments: " + this.state.experiments))
                this.entityLoaded = true
            })
    }


    componentDidUpdate(prevProps, prevState, snapshot) {
        console.log("componentDidUpdate")
        if (this.props.entity.host) {
            if (!this.entityLoaded) {
                this.entityLoaded = true
                this.propsChanged(this.props)

            }
        }
        console.log("this.state.experiments: " + this.state.experiments)


    }

    componentDidMount() {
         console.log("componentDidMount")
        if (this.props.entity.host) {
            if (!this.entityLoaded) {
                this.entityLoaded = true
                this.propsChanged(this.props)
            }
        }
        console.log("this.state.experiments: " + this.state.experiments)
    }

    render() {
        console.log("render")
        let tableValues = this.state.experiments;
        return <div>
            <ReactTable pageSize={10}
                        expanded={this.state.expanded}
                        getTrProps={(state, rowInfo, column, instance, expanded) => {
                                    return {
                                      onClick: e => {
                                        console.log(rowInfo);
                                        console.log(rowInfo.viewIndex);
                                        var rowIndex = "{'" + rowInfo.viewIndex + "':'true'}";
                                        console.log(rowIndex);
                                        this.setState({ expanded: {}, experiments : tableValues });
                                        //this.setState({ expanded: rowIndex });
                                        //this.setState({ expanded: JSON.parse(rowIndex) });
                                        this.setState({
                                          expanded: "{'" + rowInfo.viewIndex + "':'true'}",
                                          experiments : tableValues
                                        });
                                      }
                                    };
                                  }}
                        data={tableValues}
                        columns=
                                            {
                                                [{
                                                    Header: 'Experiment Id', accessor: 'experiment_id'
                                                }, {
                                                    Header: 'Experiment Name', accessor: 'name'
                                                }]
                                            }
                        resizable={true}
                        showPagination={false}
                                        collapseOnDataChange={false}
                                        minRows={1}
                                        style={{width: '100%'}}
                                SubComponent={row => {
                                    return (
                                      <div style={{ padding: "20px" }}>
                                        <p>Runs</p>
                                        <ReactTable
                                          data={row.original.runs}
                                          columns= {
                                             [{
                                                id: 'artifactUri',  Header: 'Artifact Uri', accessor: i => i.info.artifact_uri
                                             }]
                                          }
                                          showPagination={true}

                                        />
                                      </div>
                                    );
                                  }}
            />
        </div>
    }
}

export default translate()(MLFlowView);
